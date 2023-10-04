package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;

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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.DisposableChannel;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;

@Slf4j
@RequiredArgsConstructor
public abstract class TcpFluxer implements Fluxer {
	protected final String ipAddress;
	protected final Integer port;

	protected Many<Status> statusSink;
	protected Flux<Status> statusFlux;

	protected Many<Link> linkSink;
	protected Flux<Link> linkFlux;

	protected Many<Message> inboundSink;
	protected Flux<Message> inboundFlux;

	protected BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
	protected DisposableChannel disposableChannel;
	protected EventExecutor executor;
	protected ChannelGroup group;

	abstract protected void createTcpConnection(Sinks.One<Void> resultSink);

	@Override
	public Mono<Void> start() {

		Sinks.One<Void> resultSink = Sinks.one();

		initializeStreams();

		emitStatus(Status.STOPPED);

		if (!validate()) {
			emitStartException(resultSink, "Invalid configuration detected.");
		} else {
			emitStatus(Status.STARTING);

			handler = createHandler();
			executor = new DefaultEventExecutor();
			group = new DefaultChannelGroup(executor);

			createTcpConnection(resultSink);
		}

		return resultSink.asMono();
	}

	@Override
	public Mono<Void> stop() {

		if (statusSink == null || linkSink == null || inboundSink == null || disposableChannel == null || group == null
				|| executor == null) {
			Sinks.One<Void> resultSink = Sinks.one();

			emitStopException(resultSink, "Not started yet, so can't stop");
			emitStatus(Status.STOPPED);

			return Mono.empty();
		}

		emitStatus(Status.STOPPING);

		group.disconnect().addListener(new ChannelGroupFutureListener() {
			@Override
			public void operationComplete(ChannelGroupFuture future) throws Exception {

				executor.shutdownGracefully();
				group.close();
				disposableChannel.dispose();

				emitStatus(Status.STOPPED);

				statusSink.tryEmitComplete();
				linkSink.tryEmitComplete();
				inboundSink.tryEmitComplete();
			}
		});

		return disposableChannel.onDispose();
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
		// If writing to multiple links needs to be allowed,
		// - Change isSameLink() to support wildcard or regex or something else
		// - Return a new future created from multiple ChannelFuture
		// - See
		// https://stackoverflow.com/questions/10503586/writing-to-all-but-one-in-a-tcp-netty-channelgroup
		Sinks.One<Void> resultSink = Sinks.one();

		if (group != null) {
			Iterator<Channel> channelIterator = group.iterator();
			boolean linkFound = false;
			Channel channel = null;
			while (channelIterator.hasNext()) {
				channel = channelIterator.next();
				if (isSameLink(channel, message.getLink())) {
					linkFound = true;
					break;
				}
			}

			if (linkFound) {
				channel.writeAndFlush(Unpooled.wrappedBuffer(message.getMessage()))
						.addListener(new ChannelFutureListener() {
							@Override
							public void operationComplete(ChannelFuture future) throws Exception {
								log.debug("writing to " + message.getLink() + " completed: "
										+ ByteBufUtil.hexDump(message.getMessage()));
								resultSink.tryEmitEmpty();
							}
						});
			} else {
				emitWriteException(resultSink, message.getLink() + " is not connected yet.");
			}
		}

		return resultSink.asMono();
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public Integer getPort() {
		return port;
	}

	protected void emitStatus(Status status) {
		log.debug("status updated: " + status.toString());
		statusSink.tryEmitNext(status);
	}

	protected void emitLink(Connection connection, Link.Status status) {
		InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
		InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
		Link newLink = new Link(new Endpoint(local.getAddress().getHostAddress(), local.getPort()),
				new Endpoint(remote.getAddress().getHostAddress(), remote.getPort()), status);
		log.debug("link updated: " + newLink + " --> " + status.toString());
		linkSink.tryEmitNext(newLink);
	}

	protected void emitInbound(Connection connection, byte[] receivedMessage) {
		InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
		InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
		// FIXME: Assuming the link is connected, but would it be ok?
		Link newLink = new Link(new Endpoint(local.getAddress().getHostAddress(), local.getPort()),
				new Endpoint(remote.getAddress().getHostAddress(), remote.getPort()), Link.Status.CONNECTED);
		log.debug("inbound updated: " + newLink + " --> " + ByteBufUtil.hexDump(receivedMessage));
		inboundSink.tryEmitNext(new Message(newLink, receivedMessage));
	}

	private void initializeStreams() {
		statusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
		statusFlux = statusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			log.debug("A new subscriber to status flux: " + sub);
		}).log();

		linkSink = Sinks.many().multicast().<Link>onBackpressureBuffer();
		linkFlux = linkSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			log.debug("A new subscriber to link flux: " + sub);
		}).log();

		inboundSink = Sinks.many().multicast().<Message>onBackpressureBuffer();
		inboundFlux = inboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
			log.debug("A new subscriber to inbound flux: " + sub);
		}).log();
	}

	private boolean validate() {
		// Null or blank
		if (ipAddress == null || ipAddress.isBlank()) {
			log.error("Invalid host: must not be null or blank");
			return false;
		}

		// Not valid ip address
		if (port < 1024 || port > 65535) {
			log.error("Invalid port: must be >= 1024 and <= 65535");
			return false;
		}

		return true;
	}

	private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> createHandler() {
		return new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
			@Override
			public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
				in.withConnection(tcpConnection -> {
					log.debug("withConnection");
					log.debug(tcpConnection.channel().localAddress().toString());
					log.debug(tcpConnection.channel().remoteAddress().toString());
				}).receive().asByteArray().doOnCancel(() -> {
					log.debug("in doOnCancel");
				}).doOnComplete(() -> {
					log.debug("in doOnComplete");
				}).doOnNext(buf -> {
					in.withConnection(connection -> {
						System.out.println(ByteBufUtil.hexDump(buf) + " from " + connection + "???");
						log.debug(ByteBufUtil.hexDump(buf) + " from " + connection + "???");
						log.debug(connection.channel().localAddress().toString());
						log.debug(connection.channel().remoteAddress().toString());
						emitInbound(connection, buf);
					});
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

	private boolean isSameLink(Channel channel, @NonNull Link link) {
		InetSocketAddress remote = (InetSocketAddress) channel.remoteAddress();
		if (link.getRemoteEndpoint().getIpAddress().equals(remote.getAddress().getHostAddress())
				&& link.getRemoteEndpoint().getPort().equals(Integer.valueOf(remote.getPort()))) {
			return true;
		}

		return false;
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
