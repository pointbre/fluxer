package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
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
    
    private final String host;
    private final Integer port;
    
    private Many<Fluxer.Status> statusSink;
    private Flux<Fluxer.Status> statusFlux;

    private Many<Link> linkSink;
    private Flux<Link> linkFlux;

    private Many<byte[]> inboundSink;
    private Flux<byte[]> inboundFlux;

    private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
    private DisposableServer disposableServer;
    private EventExecutor executor;
    private ChannelGroup group;
    
    @Override
    public Mono<Void> start() {
	
	Sinks.One<Void> resultSink = Sinks.one();

	statusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
	statusFlux = statusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
	    log.debug("A new status subscriber! " + sub);
	}).log();

	linkSink = Sinks.many().multicast().<Link>onBackpressureBuffer();
	linkFlux = linkSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
	    log.debug("A new link subscriber! " + sub);
	}).log();

	linkInboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
	linkInboundFlux = linkInboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
	    log.debug("A new Inbound subscriber! " + sub);
	}).log();


	emitStatus(Status.STOPPED);

	if (validate()) {
	    emitStartException(resultSink, "Invalid configuration detected.");
	} else {
	    emitStatus(Status.STARTING);

	    handler = createHandler();
	    executor = new DefaultEventExecutor();
	    group = new DefaultChannelGroup(executor);

	    TcpServer tcpServer = TcpServer.create().doOnBind(tcpServerConfig -> {
		log.debug("server doOnBind " + tcpServerConfig);
	    }).doOnBound(disposableServer -> {
		log.debug("server doOnBound " + disposableServer);
	    }).doOnConnection(connection -> {
		log.debug("server doOnConnection " + connection);
		
		InetSocketAddress address = (InetSocketAddress) connection.channel().remoteAddress();
		Connection newConnection = new Connection(address.getHostString(), address.getPort());
		
		Many<byte[]> inboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
		Flux<byte[]> inboundFlux = inboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
		    log.debug("A new Inbound subscriber! " + sub);
		}).log();		
		inboundSinks.put(newConnection, inboundSink);
		inboundFluxes.put(newConnection, inboundFlux);

		group.add(connection.channel());
		
		group.stream().map(channel -> {
		    
		    return null;
		});

		log.debug("New connection added:" + connection + ", currently " + group.size());
		// TODO Use expectedConnection to determine all expected connections are
		// established
	    }).doOnUnbound(disposableServer -> {
		log.debug("server doOnUnbound " + disposableServer);
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
		}
	    }).handle(handler).host(this.host).port(this.port).wiretap(true).noSSL();

	    tcpServer.bind().subscribe(disposableServer -> {
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
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public Flux<Status> status() {
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public Flux<Link> link() {
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public Flux<Message> read() {
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public Mono<Void> write(Message message) {
	// TODO Auto-generated method stub
	return null;
    }

//    @Override
//    public Mono<Void> start() {
//
//	Sinks.One<Void> resultSink = Sinks.one();
//
//	statusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
//	statusFlux = statusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
//	    log.debug("A new Status subscriber! " + sub);
//	}).log();
//
//	connectionsSink = Sinks.many().multicast().<Connections>onBackpressureBuffer();
//	connectionsFlux = connectionsSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
//	    log.debug("A new Connections subscriber! " + sub);
//	}).log();
//
//	inboundSinks = new HashMap<>();
//	inboundFluxes = new HashMap<>();
//
//	emitStatus(Status.STOPPED);
//
//	if (validate()) {
//	    emitStartException(resultSink, "Invalid configuration detected.");
//	} else {
//	    emitStatus(Status.STARTING);
//
//	    handler = createHandler();
//	    executor = new DefaultEventExecutor();
//	    group = new DefaultChannelGroup(executor);
//
//	    TcpServer tcpServer = TcpServer.create().doOnBind(tcpServerConfig -> {
//		log.debug("server doOnBind " + tcpServerConfig);
//	    }).doOnBound(disposableServer -> {
//		log.debug("server doOnBound " + disposableServer);
//	    }).doOnConnection(connection -> {
//		log.debug("server doOnConnection " + connection);
//		
//		InetSocketAddress address = (InetSocketAddress) connection.channel().remoteAddress();
//		Connection newConnection = new Connection(address.getHostString(), address.getPort());
//		
//		Many<byte[]> inboundSink = Sinks.many().multicast().<byte[]>onBackpressureBuffer();
//		Flux<byte[]> inboundFlux = inboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
//		    log.debug("A new Inbound subscriber! " + sub);
//		}).log();		
//		inboundSinks.put(newConnection, inboundSink);
//		inboundFluxes.put(newConnection, inboundFlux);
//
//		group.add(connection.channel());
//		
//		group.stream().map(channel -> {
//		    
//		    return null;
//		});
//
//		log.debug("New connection added:" + connection + ", currently " + group.size());
//		// TODO Use expectedConnection to determine all expected connections are
//		// established
//	    }).doOnUnbound(disposableServer -> {
//		log.debug("server doOnUnbound " + disposableServer);
//	    }).doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
//		log.debug("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
//	    }).channelGroup(group).childObserve((tcpConnection, newState) -> {
//		// See ConnectionObserver.State
//		// If doOnConnection is set, this seems to affect childObserve too as the event
//		// is not passed to child observer
//
//		// There must be a subscriber to inbound stream
//		// If not, disconnection event is not reported
//		log.debug("childObserve:" + newState + ":" + tcpConnection);
//		if (newState == ConnectionObserver.State.DISCONNECTING) {
//		    log.debug("server childObserve DISCONNECTING");
//		}
//	    }).handle(handler).host(this.host).port(this.port).wiretap(true).noSSL();
//
//	    tcpServer.bind().subscribe(disposableServer -> {
//		resultSink.tryEmitEmpty();
//		this.disposableServer = disposableServer;
//		log.debug("server bind returned");
//		emitStatus(Status.STARTED);
//	    }, ex -> {
//		resultSink.tryEmitError(ex);
//		log.debug("server bind error: " + ex.getMessage());
//		emitStatus(Status.STOPPED);
//	    });
//	}
//
//	return resultSink.asMono();
//    }
//
//    @Override
//    public Mono<Void> stop() {
//
//	Sinks.One<Void> resultSink = Sinks.one();
//
//	emitStatus(Status.STOPPING);
//
//	if (disposableServer == null || group == null) {
//	    emitStopException(resultSink, "Not started yet, so can't stop");
//	    emitStatus(Status.STOPPED);
//	} else {
//	    group.disconnect().addListener(new ChannelGroupFutureListener() {
//		@Override
//		public void operationComplete(ChannelGroupFuture future) throws Exception {
//		    if (executor != null) {
//			executor.shutdownGracefully();
//		    }
//
//		    if (group != null) {
//			group.close();
//		    }
//
//		    log.debug("All connections are disconnected, so disposing server now");
//		    disposableServer.dispose();
//
//		    emitStatus(Status.STOPPED);
//
//		    log.debug("linkStatusSink.tryEmitComplete()");
//		    if (statusSink != null) {
//			statusSink.tryEmitComplete();
//		    }
//		    log.debug("linkInboundSink.tryEmitComplete()");
//		    if (inboundSink != null) {
//			inboundSink.values().forEach(sink -> sink.tryEmitComplete());
//		    }
//		}
//	    });
//	}
//
//	return disposableServer.onDispose();
//
//    }
//
//    @Override
//    public Flux<Status> status() {
//	if (statusFlux == null) {
//	    statusFlux = statusSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
//		log.debug("A new LinkStatus subscriber! " + sub);
//		// TODO Notify the latest status to the new subscriber if an option is on?
//	    }).log();
//	}
//
//	return this.statusFlux;
//
//    }
//
//    @Override
//    public Flux<Connections> connections() {
//	// TODO Auto-generated method stub
//	return null;
//    }
//
//    @Override
//    public Flux<byte[]> read(Connection connection) {
//	if (inboundFlux == null) {
//	    inboundFlux = new HashMap<Connection, >
//	    inboundFlux = inboundSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
//		log.debug("A new Inbound subscriber! " + sub);
//		// TODO Notify the latest received message to the new subscriber if an option is
//		// on?
//	    }).log();
//	}
//
//	return this.inboundFlux;
//    }
//
//    @Override
//    public Mono<Void> write(Connection connection, byte[] message) {
//	Sinks.One<Void> sink = Sinks.one();
//
//	if (group != null) {
//	    group.writeAndFlush(Unpooled.wrappedBuffer(message)).addListener(new ChannelGroupFutureListener() {
//		@Override
//		public void operationComplete(ChannelGroupFuture future) throws Exception {
//		    log.debug("writing completed");
//		    sink.tryEmitEmpty();
//		}
//	    });
//	}
//
//	return sink.asMono();
//    }

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
	if (host == null || host.isBlank()) {
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

    private void emitStatus(Status status) {
	if (statusSink == null) {
	    statusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
	}

	log.debug("Status updated: " + status.toString());
	statusSink.tryEmitNext(status);
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
	resultSink.tryEmitError(new Fluxer.StartException(errorMessage));
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
