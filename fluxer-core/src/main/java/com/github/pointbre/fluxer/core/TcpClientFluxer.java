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
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpClient;

@Slf4j
public class TcpClientFluxer implements SingleLinkFluxer {

    private String host = "localhost";
    private int port = 8421;
    private boolean logging = false;

    private TcpClient tcpClient;
    private Connection connection;
    private EventExecutor executor;
    private ChannelGroup group;

    private Many<Status> linkStatusSink;
    private Flux<Status> linkStatusFlux;
    private Many<byte[]> linkInboundSink;
    private Flux<byte[]> linkInboundFlux;

    private TcpClientFluxer() {
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
	return new TcpClientFluxer().new Builder();
    }

    public class Builder {
	private Builder() {
	}

	public Builder host(String host) {
	    TcpClientFluxer.this.host = host;
	    return this;
	}

	public Builder port(int port) {
	    TcpClientFluxer.this.port = port;
	    return this;
	}

	public Builder logging(boolean logging) {
	    TcpClientFluxer.this.logging = logging;
	    return this;
	}

	public TcpClientFluxer build() {
	    return TcpClientFluxer.this;
	}
    }

    @Override
    public Mono<Void> start() {

	Sinks.One<Void> resultSink = Sinks.one();

	// Null or blank
	if (host == null || host.isBlank()) {
	    resultSink.tryEmitError(new FluxerException("Invalid host: " + host));
	    return resultSink.asMono();
	}

	// Not valid ip address
	if (port < 1024 || port > 65535) {
	    resultSink.tryEmitError(new FluxerException("Invalid port number: " + port));
	    return resultSink.asMono();
	}

	linkStatusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
	linkStatusFlux = linkStatusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
	    log.debug("A new LinkStatus subscriber! " + sub);
	}).log();
	linkStatusSink.tryEmitNext(Status.STOPPED);

	linkInboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
	linkInboundFlux = linkInboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
	    log.debug("A new Inbound subscriber! " + sub);
	}).log();

	executor = new DefaultEventExecutor();
	group = new DefaultChannelGroup(executor);

	tcpClient = TcpClient.create().doOnResolve(connection -> {
	    log.debug("doOnResolve: " + connection);
	}).doAfterResolve((connection, socketAddress) -> {
	    log.debug("doAfterResolve: " + connection + ", " + socketAddress);
	}).doOnResolveError((connection, error) -> {
	    log.debug("doOnResolveError: " + connection + ", " + error);
	}).doOnConnect(config -> {
	    log.debug("doOnConnect: " + config);
	}).doOnConnected(connection -> {
	    log.debug("doOnConnected: " + connection);
	    group.add(connection.channel());
	    log.debug("New connection added:" + connection + ", currently " + group.size());
	}).doOnDisconnected(connection -> {
	    log.debug("doOnDisconnected: " + connection);
	}).observe((tcpConnection, newState) -> {
	    log.debug("observe:" + newState + ":" + tcpConnection);
	}).handle(new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
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
		    linkInboundSink.tryEmitNext(buf);
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
	}).host(host).port(port).wiretap(logging).noSSL();

	tcpClient.connect().subscribe(connection -> {
	    this.connection = connection;
	    resultSink.tryEmitEmpty();
	    log.debug("client connect() returned");
	}, ex -> {
	    resultSink.tryEmitError(ex);
	    log.debug("client connection() error: " + ex.getMessage());
	});

	return resultSink.asMono();
    }

    @Override
    public Mono<Void> stop() {

	if (connection == null) {
	    // This is an error
	    return Mono.<Void>empty();
	}

	if (group != null) {
	    group.disconnect().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    log.debug("All connections are disconnected, so disposing connection now");
		    connection.dispose();

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
		}
	    });
	}

	return connection.onDispose();
    }

    @Override
    public Flux<Status> status() {
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
