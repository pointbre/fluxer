package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;
import org.slf4j.event.Level;
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

    private static final int MAX_WAIT = 5;
    private final String ipAddress;
    private final Integer port;

    protected BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
    protected DisposableChannel disposableChannel;
    protected EventExecutor executor;
    protected ChannelGroup group;

    public AbstractTcpFluxer(String ipAddress, Integer port) throws Exception {
	super();

	if (ipAddress == null || ipAddress.isBlank()) {
	    Exception exception = new IllegalArgumentException("Invalid ip address: must not be null or blank");
	    emitLog(Level.ERROR, "The given ip address is invalid", exception);
	    throw exception;
	}
	if (port <= 0 || port >= 65536) {
	    Exception exception = new IllegalArgumentException("Invalid port: must be >= 1 and <= 65535");
	    emitLog(Level.ERROR, "The given port number is invalid", exception);
	    throw exception;
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
			String log = "START_REQUESTED event wasn't accepted as it's currently "
				+ getFluxerMachineState();
			resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, log));
			removeResultSink(Fluxer.State.Event.START_REQUESTED);
			emitLog(Level.ERROR, log);
		    } else {
			emitLog(Level.INFO, "Sent START_REQUESTED successfully");
		    }
		}, error -> {
		    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, error.getLocalizedMessage()));
		    removeResultSink(Fluxer.State.Event.START_REQUESTED);
		    emitLog(Level.ERROR, "Failed to send START_REQUESTED event:" + error.getLocalizedMessage(), error);
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
			String log = "STOP_REQUESTED event wasn't accepted as it's currently "
				+ getFluxerMachineState();
			resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, log));
			removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
			emitLog(Level.ERROR, log);
		    } else {
			emitLog(Level.INFO, "Sent START_REQUESTED successfully");
		    }
		}, error -> {
		    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, error.getLocalizedMessage()));
		    removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
		    emitLog(Level.ERROR, "Failed to send STOP_REQUESTED event:" + error.getLocalizedMessage());
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
		final Fluxer.EndPoint local = new Fluxer.EndPoint(localAddress.getAddress().getHostAddress(),
			localAddress.getPort());
		channel.writeAndFlush(Unpooled.wrappedBuffer(message))
			.addListener(new ChannelFutureListener() {
			    @Override
			    public void operationComplete(ChannelFuture future) throws Exception {
				String log = "Successfully sent to " + remote + ":" + ByteBufUtil.hexDump(message);
				resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.PROCESSED, log));
				emitMessage(Fluxer.Message.Type.OUTBOUND, local, remote, message);
				emitLog(Level.INFO, log);
			    }
			});
	    } else {
		String log = "Failed to send a message as matching link is not found: " + remote;
		resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, log));
		emitLog(Level.ERROR, log);
	    }
	} else {
	    String log = "Failed to send a message as no link is not found: " + remote;
	    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, log));
	    emitLog(Level.ERROR, log);
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
				    String log = "Successfully stopped";
				    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.PROCESSED, log));
				    removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
				    emitLog(Level.INFO, log);
				} else {
				    emitLog(Level.ERROR,
					    "Not possible to reply PROCESSED as the result sink of STOP_REQUESTED is not available");
				}
			    } else {
				if (resultSink != null) {
				    String log = "PROCESSED event wasn't accepted as it's currently "
					    + getFluxerMachineState();
				    resultSink.tryEmitValue(new Fluxer.Result(Fluxer.Result.Type.FAILED, log));
				    removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
				    emitLog(Level.ERROR, log);
				} else {
				    emitLog(Level.ERROR,
					    "Not possible to reply FAILED as the result sink of STOP_REQUESTED is not available");
				}
			    }
			}, error -> {
			    if (resultSink != null) {
				resultSink.tryEmitValue(
					new Fluxer.Result(Fluxer.Result.Type.FAILED, error.getLocalizedMessage()));
				removeResultSink(Fluxer.State.Event.STOP_REQUESTED);
				emitLog(Level.ERROR,
					"Failed to send START_REQUESTED event:" + error.getLocalizedMessage(), error);
			    } else {
				emitLog(Level.ERROR,
					"Not possible to reply FAILED as the result sink of STOP_REQUESTED is not available");
			    }
			});
	    }
	};
    }

    protected void closeLinks() {
	emitLog(Level.INFO, "Closing link related resources");

	final CountDownLatch countDownLatch1 = new CountDownLatch(1);
	if (disposableChannel != null) {
	    disposableChannel.dispose();
	    disposableChannel
		    .onDispose()
		    .doOnError(error -> {
			emitLog(Level.ERROR, "Failed to close channel", error);
			countDownLatch1.countDown();
		    })
		    .doOnSuccess(__ -> {
			emitLog(Level.INFO, "Closed channel");
			countDownLatch1.countDown();
		    })
		    .subscribe();
	} else {
	    emitLog(Level.INFO, "No need of closing channel as it's null");
	    countDownLatch1.countDown();
	}
	try {
	    countDownLatch1.await(MAX_WAIT, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	final CountDownLatch countDownLatch2 = new CountDownLatch(1);
	if (group != null) {
	    group.disconnect().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    emitLog(Level.INFO, "Disconnected group");
		    countDownLatch2.countDown();
		}
	    });
	} else {
	    emitLog(Level.INFO, "No need of disconnecting group as it's null");
	    countDownLatch2.countDown();
	}
	try {
	    countDownLatch2.await(MAX_WAIT, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	final CountDownLatch countDownLatch3 = new CountDownLatch(1);
	if (group != null) {
	    group.close().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    emitLog(Level.INFO, "Closed group");
		    countDownLatch3.countDown();
		}
	    });
	} else {
	    emitLog(Level.INFO, "No need of closing channel as it's null");
	    countDownLatch3.countDown();
	}
	try {
	    countDownLatch3.await(MAX_WAIT, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	final CountDownLatch countDownLatch4 = new CountDownLatch(1);
	if (executor != null) {
	    executor.shutdownGracefully().addListener(new GenericFutureListener<Future<Object>>() {
		@Override
		public void operationComplete(Future<Object> future) throws Exception {
		    emitLog(Level.INFO, "Closed executor");
		    countDownLatch4.countDown();
		}
	    });
	} else {
	    emitLog(Level.INFO, "No need of closing executor as it's null");
	    countDownLatch4.countDown();
	}
	try {
	    countDownLatch4.await(MAX_WAIT, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}

	disposableChannel = null;
	group = null;
	executor = null;
	emitLog(Level.INFO, "Link related resources are closed");
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
		in.receive().asByteArray().doOnNext(buf -> {
		    in.withConnection(connection -> {
			InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
			InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
			emitMessage(Fluxer.Message.Type.INBOUND,
				new Fluxer.EndPoint(local.getAddress().getHostAddress(), local.getPort()),
				new Fluxer.EndPoint(remote.getAddress().getHostAddress(), remote.getPort()), buf);
			String log = "A new message is received from " + connection.channel().localAddress().toString();
			emitLog(Level.INFO, log);
		    });
		}).subscribe();

		return out.neverComplete();
	    }
	};
    }

}
