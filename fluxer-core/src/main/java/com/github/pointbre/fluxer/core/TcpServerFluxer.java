package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;

import io.netty.buffer.Unpooled;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;

@Slf4j
@RequiredArgsConstructor
public class TcpServerFluxer implements Fluxer {

	private final String localIPAddress;
	private final Integer localPort;

	private Many<Status> statusSink;
	private Flux<Status> statusFlux;

	private Many<Link> linkSink;
	private Flux<Link> linkFlux;

	private Many<Message> inboundSink;
	private Flux<Message> inboundFlux;

	private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
	private DisposableServer disposableServer;
	private EventExecutor executor;
	private ChannelGroup group;

	@Override
	public Mono<Void> start() {

		Sinks.One<Void> resultSink = Sinks.one();

		statusSink = Sinks.many()
				.multicast()
				.<Status>onBackpressureBuffer();

		statusFlux = statusSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(sub -> {
					log.debug("A new subscriber to status flux: " + sub);
				})
				.log();

		linkSink = Sinks.many()
				.multicast()
				.<Link>onBackpressureBuffer();
		linkFlux = linkSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(sub -> {
					log.debug("A new subscriber to link flux: " + sub);
				})
				.log();

		inboundSink = Sinks.many()
				.multicast()
				.<Message>onBackpressureBuffer();
		inboundFlux = inboundSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(sub -> {
					log.debug("A new subscriber to inbound flux: " + sub);
				})
				.log();

		emitStatus(Status.STOPPED);

		if (validate()) {
			emitStartException(resultSink, "Invalid configuration detected.");
		} else {
			emitStatus(Status.STARTING);

			handler = createHandler();
			executor = new DefaultEventExecutor();
			group = new DefaultChannelGroup(executor);

			TcpServer tcpServer = TcpServer.create()
					.doOnBind(tcpServerConfig -> {
						log.debug("server doOnBind " + tcpServerConfig);
					})
					.doOnBound(disposableServer -> {
						log.debug("server doOnBound " + disposableServer);
					})
					.doOnConnection(connection -> {
						log.debug("server doOnConnection " + connection);

						group.add(connection.channel());
						log.debug("New connection added:" + connection + ", currently " + group.size());

						emitLink(connection, Link.Status.CONNECTED);

					})
					.doOnUnbound(disposableServer -> {
						log.debug("server doOnUnbound " + disposableServer);
					})
					.doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
						log.debug("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
					})
					.channelGroup(group).childObserve((connection, newState) -> {
						// See ConnectionObserver.State
						// If doOnConnection is set, this seems to affect childObserve too, as the event
						// is not passed to child observer

						// There must be a subscriber to inbound stream.
						// If not, disconnection event is not reported.
						log.debug("childObserve:" + newState + ":" + connection);
						if (newState == ConnectionObserver.State.DISCONNECTING) {
							log.debug("server childObserve DISCONNECTING");
							emitLink(connection, Link.Status.DISCONNECTED);
						}
					})
					.handle(handler)
					.host(this.localIPAddress)
					.port(this.localPort)
					.wiretap(true)
					.noSSL();

			tcpServer.bind()
					.subscribe(disposableServer -> {
						resultSink.tryEmitEmpty();
						this.disposableServer = disposableServer;
						log.debug("server bind returned");
						emitStatus(Status.STARTED);
					}, ex -> {
						resultSink.tryEmitError(ex);
						log.debug("server bind error: " + ex.getMessage());
						emitStatus(Status.STOPPED);
					});
		}

		return resultSink.asMono();
	}

	@Override
	public Mono<Void> stop() {
		Sinks.One<Void> resultSink = Sinks.one();

		if (statusSink == null ||
				linkSink == null ||
				inboundSink == null ||
				disposableServer == null ||
				group == null ||
				executor == null) {
			emitStopException(resultSink, "Not started yet, so can't stop");
			emitStatus(Status.STOPPED);
		} else {
			emitStatus(Status.STOPPING);

			group.disconnect().addListener(new ChannelGroupFutureListener() {
				@Override
				public void operationComplete(ChannelGroupFuture future) throws Exception {

					executor.shutdownGracefully();

					group.close();

					disposableServer.dispose();

					emitStatus(Status.STOPPED);

					statusSink.tryEmitComplete();
					linkSink.tryEmitComplete();
					inboundSink.tryEmitComplete();
				}
			});
		}

		return disposableServer.onDispose();
	}

	@Override
	public Flux<Status> status() {
		return statusFlux;
	}

	@Override
	public Flux<Link> link() {
		return linkFlux;
	}

	@Override
	public Flux<Message> read() {
		return inboundFlux;
	}

	@Override
	public Mono<Void> write(Message message) {
		Sinks.One<Void> sink = Sinks.one();

		// TODO: Write only to the given Link included in message
		if (group != null) {
			group.writeAndFlush(Unpooled.wrappedBuffer(message.getMessage()))
					.addListener(new ChannelGroupFutureListener() {
						@Override
						public void operationComplete(ChannelGroupFuture future) throws Exception {
							log.debug("writing completed");
							sink.tryEmitEmpty();
						}
					});

		}

		return sink.asMono();
	}

	private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> createHandler() {
		return new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
			@Override
			public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
				in.withConnection((tcpConnection) -> {
					log.debug("withConnection");
					log.debug(tcpConnection.channel().localAddress().toString());
					log.debug(tcpConnection.channel().remoteAddress().toString());
				}).receive().asByteArray().doOnCancel(() -> {
					log.debug("in doOnCancel");
				}).doOnComplete(() -> {
					log.debug("in doOnComplete");
				}).doOnNext(buf -> {
					emitInbound(buf);
				}).doOnError(e -> {
					log.debug("in doOnError " + e);
				}).doOnSubscribe(s -> {
					log.debug("in doOnSubscribe " + s);
				}).doOnTerminate(() -> {
					log.debug("in doOnTerminate");
				}).subscribe();

				return out.neverComplete().doOnTerminate(() -> {
					log.debug("out doOnTerminate");
				}).doOnError(ex -> {
					log.debug("out doOnError: " + ex.getMessage());
				}).doOnCancel(() -> {
					log.debug("out doOnCancel");
				});
			}
		};
	}

	private boolean validate() {
		// Null or blank
		if (localIPAddress == null || localIPAddress.isBlank()) {
			log.error("Invalid host: must not be null or blank");
			return false;
		}

		// Not valid ip address
		if (localPort < 1024 || localPort > 65535) {
			log.error("Invalid port: must be >= 1024 and <= 65535");
			return false;
		}

		return true;
	}

	private void emitStatus(Status status) {
		if (statusSink == null) {
			statusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
		}

		log.debug("Status updated: " + status.toString());
		statusSink.tryEmitNext(status);
	}

	private void emitLink(Connection connection, Link.Status status) {
		InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
		InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
		Link newLink = new Link(local.getAddress().getHostAddress(), local.getPort(),
				remote.getAddress().getHostAddress(), remote.getPort(), status);
		linkSink.tryEmitNext(newLink);
	}

	private void emitInbound(byte[] receivedMessage) {
		if (inboundSink == null) {
			inboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
		}

		log.debug("Inbound updated: " + receivedMessage);
		inboundSink.tryEmitNext(receivedMessage);
	}

	private void emitStartException(Sinks.One<Void> resultSink, String errorMessage) {
		log.error(errorMessage);
		resultSink.tryEmitError(new StartException(errorMessage));
	}

	private void emitStopException(Sinks.One<Void> resultSink, String errorMessage) {
		log.error(errorMessage);
		resultSink.tryEmitError(new StopException(errorMessage));
	}

	private void emitWriteException(Sinks.One<Void> resultSink, String errorMessage) {
		log.error(errorMessage);
		resultSink.tryEmitError(new WriteException(errorMessage));
	}
}
