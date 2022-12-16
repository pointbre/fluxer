package com.github.pointbre.fluxer.core;

import java.util.function.BiFunction;

import org.reactivestreams.Publisher;

import io.netty.buffer.Unpooled;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ConnectionObserver;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;

public class TcpServerLink implements Link {
	// TODO Use builder pattern

	private String host = "localhost";
	private int port = 8421;
	private boolean logging = false;

	private DisposableServer disposableServer;
	private TcpServer tcpServer;
	private EventExecutor executor;
	private ChannelGroup group;

	private Many<Status> linkStatusSink;
	private Flux<Status> linkStatusFlux;
	private Many<byte[]> linkInboundSink;
	private Flux<byte[]> linkInboundFlux;

	@Override
	public Mono<Void> initialize() {
		executor = new DefaultEventExecutor();
		group = new DefaultChannelGroup(executor);

		tcpServer = TcpServer.create().doOnBind(tcpServerConfig -> {
			System.out.println("server doOnBind " + tcpServerConfig);
			linkStatusSink.tryEmitNext(Status.STARTING);
		}).doOnBound(disposableServer -> {
			System.out.println("server doOnBound " + disposableServer);
			linkStatusSink.tryEmitNext(Status.CONNECTING);
		}).doOnConnection(tcpConnection -> {
			System.out.println("server doOnConnection " + tcpConnection);

			group.add(tcpConnection.channel());
			System.out.println("New connection added:" + tcpConnection + ", currently " + group.size());

			linkStatusSink.tryEmitNext(Status.CONNECTED);
		}).doOnUnbound(disposableServer -> {
			System.out.println("server doOnUnbound " + disposableServer);
			linkStatusSink.tryEmitNext(Status.STOPPING);
		}).doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
			System.out.println("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
		}).channelGroup(group).childObserve((tcpConnection, newState) -> {
			// See ConnectionObserver.State
			// If doOnConnection is set, this seems to affect childObserve too as the event
			// is not passed to child observer

			// There must be a subscriber to inbound stream
			// If not, disconnection event is not reported
			System.out.println("childObserve:" + newState + ":" + tcpConnection);
			if (newState == ConnectionObserver.State.DISCONNECTING) {
				System.out.println("server childObserve DISCONNECTING");
				linkStatusSink.tryEmitNext(Status.STOPPING);
			}
		}).handle(new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
			@Override
			public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
				in.withConnection((tcpConnection) -> {
					System.out.println("withConnection");
					System.out.println(tcpConnection.channel().localAddress());
					System.out.println(tcpConnection.channel().remoteAddress());
				}).receive().asByteArray().doOnCancel(() -> System.out.println("in doOnCancel"))
						.doOnComplete(() -> System.out.println("in doOnComplete")).doOnNext(buf -> {
							linkInboundSink.tryEmitNext(buf);
						}).doOnError(e -> System.out.println("in doOnError " + e))
						.doOnSubscribe(s -> System.out.println("in doOnSubscribe " + s))
						.doOnTerminate(() -> System.out.println("in doOnTerminate")).subscribe();

				return out.neverComplete().doOnTerminate(() -> System.out.println("out doOnTerminate"))
						.doOnError(ex -> System.out.println("out doOnError: " + ex.getMessage()))
						.doOnCancel(() -> System.out.println("out doOnCancel"));
			}
		}).host(this.host).port(this.port).wiretap(this.logging).noSSL();

		linkStatusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
		linkStatusFlux = linkStatusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			System.out.println("A new LinkStatus subscriber! " + sub);
		}).log();
		linkInboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
		linkInboundFlux = linkInboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			System.out.println("A new Inbound subscriber! " + sub);
		}).log();

		System.out.println("initialize() done");

		return Mono.<Void>empty();
	}

	@Override
	public Mono<Void> destroy() {
		Sinks.One<Void> sink = Sinks.one();

		stop().then().doFinally(signal -> {
			System.out.println("doFinally triggered");
			System.out.println("executor.shutdownGracefully()");
			if (executor != null) {
				executor.shutdownGracefully();
			}
			System.out.println("group.close()");
			if (group != null) {
				group.close();
			}
			System.out.println("linkStatusSink.tryEmitComplete()");
			if (linkStatusSink != null) {
				linkStatusSink.tryEmitComplete();
			}
			System.out.println("linkInboundSink.tryEmitComplete()");
			if (linkInboundSink != null) {
				linkInboundSink.tryEmitComplete();
			}
			sink.tryEmitEmpty();
		}).subscribe(disposable -> {
			System.out.println("stop() returned");
		}, ex -> {
			System.out.println("stop() error: " + ex.getMessage());
		});

		System.out.println("destroy() done");
		return sink.asMono();
	}

	@Override
	public Mono<Void> start() {
		Sinks.One<Void> sink = Sinks.one();

		if (tcpServer == null) {
			sink.tryEmitEmpty();
		} else {
			tcpServer.bind().subscribe(disposableServer -> {
				this.disposableServer = disposableServer;
				sink.tryEmitEmpty();
				System.out.println("server bind returned");
			}, ex -> {
				sink.tryEmitError(ex);
				System.out.println("server bind error: " + ex.getMessage());
			});
		}

		return sink.asMono();
	}

	@Override
	public Mono<Void> stop() {
		if (disposableServer == null) {
			return Mono.<Void>empty();
		}

		if (group != null) {
			group.disconnect().addListener(new ChannelGroupFutureListener() {
				@Override
				public void operationComplete(ChannelGroupFuture future) throws Exception {
					System.out.println("All connections are disconnected, so disposing server now");
					disposableServer.dispose();
				}
			});
		}

		return disposableServer.onDispose();
	}

	@Override
	public Flux<Status> monitor() {
		return this.linkStatusFlux;

	}

	@Override
	public Flux<byte[]> read() {
		return this.linkInboundFlux;
	}

	@Override
	public Mono<Void> write(byte[] message) {
		Sinks.One<Void> sink = Sinks.one();

		if (group != null) {
			group.writeAndFlush(Unpooled.wrappedBuffer(message)).addListener(new ChannelGroupFutureListener() {
				@Override
				public void operationComplete(ChannelGroupFuture future) throws Exception {
					System.out.println("writing completed");
					sink.tryEmitEmpty();
				}
			});
		}

		return sink.asMono();
	}
}
