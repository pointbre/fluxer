package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableChannel;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;

public abstract class AbstractTcpFluxer extends AbstractFluxer<byte[]> implements TcpFluxer<byte[]> {

    private final String ipAddress;
    private final Integer port;

    protected BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
    protected DisposableChannel disposableChannel;
    protected EventExecutor executor;
    protected ChannelGroup group;

    public AbstractTcpFluxer(String ipAddress, Integer port) throws Exception {
	super();

	if (ipAddress == null || ipAddress.isBlank()) {
	    throw new IllegalArgumentException("Invalid ip address: must not be null or blank");
	}
	if (port <= 0 || port >= 65536) {
	    throw new IllegalArgumentException("Invalid port: must be >= 1 and <= 65535");
	}

	this.ipAddress = ipAddress;
	this.port = port;
    }

    @Override
    public Mono<Fluxer.Result> start() {
	Sinks.One<Fluxer.Result> resultSink = Sinks.one();

	putResultSink(Fluxer.State.Event.START_REQUESTED, resultSink);
	sendEvent(Fluxer.State.Event.START_REQUESTED)
		.subscribe(results -> {
		    if (!isEventAccepted(results)) {
			resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED,
				"The request can't be accepted as it's currently " + getFluxerMachineState()));
			removeResultSink(Fluxer.State.Event.START_REQUESTED);
		    }
		}, error -> {
		    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, error.getLocalizedMessage()));
		    removeResultSink(Fluxer.State.Event.START_REQUESTED);
		});

	return resultSink.asMono();
    }

    @Override
    public Mono<Fluxer.Result> stop() {
	Sinks.One<Fluxer.Result> resultSink = Sinks.one();

	putResultSink(Fluxer.State.Event.STOP_REQUESTED, resultSink);
	sendEvent(Fluxer.State.Event.STOP_REQUESTED)
		.subscribe(results -> {
		    if (!isEventAccepted(results)) {
			resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED,
				"The request can't be accepted as it's currently " + getFluxerMachineState()));
			removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
		    }
		}, error -> {
		    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, error.getLocalizedMessage()));
		    removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
		});

	return resultSink.asMono();
    }

    @Override
    public Mono<Fluxer.Result> send(byte[] message, Fluxer.EndPoint remote) {
	Sinks.One<Fluxer.Result> resultSink = Sinks.one();

	if (group != null) {
	    Iterator<Channel> channelIterator = group.iterator();
	    boolean linkFound = false;
	    Channel channel = null;
	    while (channelIterator.hasNext()) {
		channel = channelIterator.next();
		if (isSameLink(channel, remote)) {
		    linkFound = true;
		    break;
		}
	    }

	    if (linkFound) {
		InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
		final Fluxer.EndPoint local = new Fluxer.EndPoint(localAddress.getAddress().getHostAddress(), localAddress.getPort());
		channel.writeAndFlush(Unpooled.wrappedBuffer(message))
			.addListener(new ChannelFutureListener() {
			    @Override
			    public void operationComplete(ChannelFuture future) throws Exception {
				System.out.println("writing result=" + future);
				resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.PROCESSED,
					"Successfully sent to " + remote + ":" + ByteBufUtil.hexDump(message)));
				emitMessage(Fluxer.Message.Type.OUTBOUND, local, remote, message);
				
			    }
			});
	    } else {
		resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, "Matching link is not found: " + remote));
	    }
	}

	return resultSink.asMono();
    }

    @Override
    public String getIpAddress() {
	return ipAddress;
    }

    @Override
    public Integer getPort() {
	return port;
    }

    abstract protected void createTcpConnection();

    protected Action<Fluxer.State.Type, Fluxer.State.Event> processStartRequest() {
	return new Action<Fluxer.State.Type, Fluxer.State.Event>() {

	    @Override
	    public void execute(StateContext<Fluxer.State.Type, Fluxer.State.Event> context) {
		handler = createHandler();
		executor = new DefaultEventExecutor();
		group = new DefaultChannelGroup(executor);
		createTcpConnection();
	    }
	};
    }

    protected Action<Fluxer.State.Type, Fluxer.State.Event> processStopRequest() {
	return new Action<Fluxer.State.Type, Fluxer.State.Event>() {

	    @Override
	    public void execute(StateContext<Fluxer.State.Type, Fluxer.State.Event> context) {
		closeLinks();

		Sinks.One<Result> resultSink = getResultSink(Fluxer.State.Event.STOP_REQUESTED);

		sendEvent(Fluxer.State.Event.PROCESSED)
			.subscribe(results -> {
			    if (isEventAccepted(results)) {
				if (resultSink != null) {
				    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.PROCESSED, "Successfully stopped"));
				    removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
				}
			    } else {
				if (resultSink != null) {
				    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED,
					    "The request can't be accepted as it's currently " + getFluxerMachineState()));
				    removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
				}
			    }
			}, error -> {
			    if (resultSink != null) {
				resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, error.getLocalizedMessage()));
				removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
			    }
			});
	    }
	};
    }

    protected void closeLinks() {
	final CountDownLatch countDownLatch1 = new CountDownLatch(1);
	if (disposableChannel != null) {
	    disposableChannel.dispose();
	    disposableChannel
		    .onDispose()
		    .doOnError(err -> {
			countDownLatch1.countDown();
		    })
		    .doOnSuccess(__ -> {
			countDownLatch1.countDown();
		    })
		    .subscribe();
	} else {
	    countDownLatch1.countDown();
	}
	try {
	    countDownLatch1.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	final CountDownLatch countDownLatch2 = new CountDownLatch(1);
	if (group != null) {
	    group.disconnect().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    countDownLatch2.countDown();
		}
	    });
	} else {
	    countDownLatch2.countDown();
	}
	try {
	    countDownLatch2.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	final CountDownLatch countDownLatch3 = new CountDownLatch(1);
	if (group != null) {
	    group.close().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    countDownLatch3.countDown();
		}
	    });
	} else {
	    countDownLatch3.countDown();
	}
	try {
	    countDownLatch3.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	final CountDownLatch countDownLatch4 = new CountDownLatch(1);
	if (executor != null) {
	    executor.shutdownGracefully().addListener(new GenericFutureListener<Future<Object>>() {
		@Override
		public void operationComplete(Future<Object> future) throws Exception {
		    countDownLatch4.countDown();
		}
	    });
	} else {
	    countDownLatch4.countDown();
	}
	try {
	    countDownLatch4.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}
	disposableChannel = null;
	group = null;
	executor = null;
    }

    private boolean isSameLink(@NonNull Channel channel, @NonNull EndPoint remote) {
	InetSocketAddress channelRemoteAddress = (InetSocketAddress) channel.remoteAddress();
	if (remote.getIpAddress().equals(channelRemoteAddress.getAddress().getHostAddress())
		&& remote.getPort().equals(Integer.valueOf(channelRemoteAddress.getPort()))) {
	    return true;
	}	

	return false;
    }

    private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> createHandler() {
	return new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
	    @Override
	    public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
		in.withConnection(tcpConnection -> {
//		    System.out.println("withConnection");
//		    System.out.println(tcpConnection.channel().localAddress().toString());
//		    System.out.println(tcpConnection.channel().remoteAddress().toString());
		}).receive().asByteArray().doOnCancel(() -> {
//		    System.out.println("in doOnCancel");
		}).doOnComplete(() -> {
//		    System.out.println("in doOnComplete");
		}).doOnNext(buf -> {
		    in.withConnection(connection -> {
//						System.out.println(ByteBufUtil.hexDump(buf) + " from " + connection + "???");
			System.out.println("received:" +ByteBufUtil.hexDump(buf));
			System.out.println("local:" + connection.channel().localAddress().toString());
			System.out.println("remote:" + connection.channel().remoteAddress().toString());
			InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
			InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
			emitMessage(Fluxer.Message.Type.INBOUND,
				new Fluxer.EndPoint(local.getAddress().getHostAddress(), local.getPort()),
				new Fluxer.EndPoint(remote.getAddress().getHostAddress(), remote.getPort()), buf);

		    });
		}).doOnError(e -> {
//		    System.out.println("in doOnError " + e);
		}).doOnSubscribe(s -> {
//		    System.out.println("in doOnSubscribe " + s);
		}).doOnTerminate(() -> {
//		    System.out.println("in doOnTerminate");
		}).subscribe();

		return out.neverComplete().doOnTerminate(() -> {
//		    System.out.println("out doOnTerminate");
		}).doOnError(ex -> {
//		    System.out.println("out doOnError: " + ex.getMessage());
		}).doOnCancel(() -> {
//		    System.out.println("out doOnCancel");
		});
	    }
	};
    }

}
