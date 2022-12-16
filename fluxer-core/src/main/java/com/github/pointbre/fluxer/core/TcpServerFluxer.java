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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class TcpServerFluxer implements Fluxer {

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
	
	private TcpServerFluxer() {
	}
	
	public String host() {
		return host;
	}

	public int port() {
		return port;
	}

	public boolean logging() {
		return logging;
	}
	
	public static Builder builder() {
		return new TcpServerFluxer().new Builder();
	}
	
	public class Builder {
		private Builder() {
		}
		
		public Builder host(String host) {
			TcpServerFluxer.this.host = host;
			return this;
		}
		
		public Builder port(int port) {
			TcpServerFluxer.this.port = port;
			return this;
		}
		
		public Builder logging(boolean logging) {
			TcpServerFluxer.this.logging = logging;
			return this;
		}
		
		public TcpServerFluxer build() {
			return TcpServerFluxer.this;
		}
	}

	@Override
	public Mono<Void> initialize() {
		Sinks.One<Void> sink = Sinks.one();
		
		linkStatusSink.tryEmitNext(Status.STOPPED);
		
		if (host == null || host.isBlank()) {
			sink.tryEmitError(null);
		}
		if (port < 1024 || port > 65535) {
			sink.tryEmitError(new IllegalArgumentException(host));
		}
		
		executor = new DefaultEventExecutor();
		group = new DefaultChannelGroup(executor);

		tcpServer = TcpServer.create().doOnBind(tcpServerConfig -> {
			log.debug("server doOnBind " + tcpServerConfig);
			linkStatusSink.tryEmitNext(Status.STARTING);
		}).doOnBound(disposableServer -> {
			log.debug("server doOnBound " + disposableServer);
			linkStatusSink.tryEmitNext(Status.CONNECTING);
		}).doOnConnection(tcpConnection -> {
			log.debug("server doOnConnection " + tcpConnection);

			group.add(tcpConnection.channel());
			log.debug("New connection added:" + tcpConnection + ", currently " + group.size());

			linkStatusSink.tryEmitNext(Status.CONNECTED);
		}).doOnUnbound(disposableServer -> {
			log.debug("server doOnUnbound " + disposableServer);
			linkStatusSink.tryEmitNext(Status.STOPPING);
		}).doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
			log.debug("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
		}).channelGroup(group).childObserve((tcpConnection, newState) -> {
			// See ConnectionObserver.State
			// If doOnConnection is set, this seems to affect childObserve too as the event
			// is not passed to child observer

			// There must be a subscriber to inbound stream
			// If not, disconnection event is not reported
			log.debug("childObserve:" + newState + ":" + tcpConnection);
			if (newState == ConnectionObserver.State.DISCONNECTING) {
				log.debug("server childObserve DISCONNECTING");
				linkStatusSink.tryEmitNext(Status.STOPPING);
			}
		}).handle(new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
			@Override
			public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
				in.withConnection((tcpConnection) -> {
					log.debug("withConnection");
					log.debug(tcpConnection.channel().localAddress().toString());
					log.debug(tcpConnection.channel().remoteAddress().toString());
				}).receive().asByteArray().doOnCancel(() -> log.debug("in doOnCancel"))
						.doOnComplete(() -> log.debug("in doOnComplete")).doOnNext(buf -> {
							linkInboundSink.tryEmitNext(buf);
						})
						.doOnError(e -> log.debug("in doOnError " + e))
						.doOnSubscribe(s -> log.debug("in doOnSubscribe " + s))
						.doOnTerminate(() -> log.debug("in doOnTerminate")).subscribe();

				return out.neverComplete().doOnTerminate(() -> log.debug("out doOnTerminate"))
						.doOnError(ex -> log.debug("out doOnError: " + ex.getMessage()))
						.doOnCancel(() -> log.debug("out doOnCancel"));
			}
		}).host(this.host).port(this.port).wiretap(this.logging).noSSL();

		linkStatusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
		linkStatusFlux = linkStatusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			log.debug("A new LinkStatus subscriber! " + sub);
		}).log();
		linkInboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
		linkInboundFlux = linkInboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			log.debug("A new Inbound subscriber! " + sub);
		}).log();

		log.debug("initialize() done");

		return sink.asMono();
	}

	@Override
	public Mono<Void> destroy() {
		Sinks.One<Void> sink = Sinks.one();

		stop().then().doFinally(signal -> {
			log.debug("doFinally triggered");
			log.debug("executor.shutdownGracefully()");
			if (executor != null) {
				executor.shutdownGracefully();
			}
			log.debug("group.close()");
			if (group != null) {
				group.close();
			}
			log.debug("linkStatusSink.tryEmitComplete()");
			if (linkStatusSink != null) {
				linkStatusSink.tryEmitComplete();
			}
			log.debug("linkInboundSink.tryEmitComplete()");
			if (linkInboundSink != null) {
				linkInboundSink.tryEmitComplete();
			}
			sink.tryEmitEmpty();
		}).subscribe(disposable -> {
			log.debug("stop() returned");
		}, ex -> {
			log.debug("stop() error: " + ex.getMessage());
		});

		log.debug("destroy() done");
		
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
				log.debug("server bind returned");
			}, ex -> {
				sink.tryEmitError(ex);
				log.debug("server bind error: " + ex.getMessage());
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
					log.debug("All connections are disconnected, so disposing server now");
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
					log.debug("writing completed");
					sink.tryEmitEmpty();
				}
			});
		}

		return sink.asMono();
	}
}
