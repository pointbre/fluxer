package com.github.pointbre.fluxer.core;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
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
	
	// start and stop --> stream
	
    private DisposableServer disposableServer;
    private TcpServer tcpServer;
    private Connection connection;
    private EventExecutor executor = new DefaultEventExecutor();
    private ChannelGroup group = new DefaultChannelGroup(executor);
    private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> ioHandler;
    
    // Reactor netty's internal streams seem to be closed when connection is terminated
    // So, would be better to create a flux which survives regardless of connection/disconnection    
    // https://hilla.dev/blog/interactive-applications-with-server-push/
    private Many<Status> linkStatusSink;
    private Many<ByteBuffer> linkInboundSink;
    
    

	public TcpServerLink() {
		
//        linkStatusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
//        linkInboundSink = Sinks.many().multicast().<ByteBuffer>onBackpressureBuffer();
		
      linkStatusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
      linkInboundSink = Sinks.many().multicast().<ByteBuffer>onBackpressureBuffer();
        
        ioHandler = new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
            @Override
            public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {

                in.withConnection((tcpConnection) -> {
                    System.out.println("withConnection");
                    System.out.println(tcpConnection.channel().localAddress());
                    System.out.println(tcpConnection.channel().remoteAddress());
                }).receive().asByteBuffer()
                        .doOnCancel(() -> System.out.println("in doOnCancel"))
                        .doOnComplete(() -> System.out.println("in doOnComplete"))
                        .doOnNext(buf -> {
                            linkInboundSink.emitNext(buf, EmitFailureHandler.FAIL_FAST);
                        })
                        .doOnError(e -> System.out.println("in doOnError " + e))
                        .doOnSubscribe(s -> System.out.println("in doOnSubscribe " + s))
                        .doOnTerminate(() -> System.out.println("in doOnTerminate"))
                        .subscribe();

                return out.neverComplete().doOnTerminate(() -> System.out.println("out doOnTerminate"))
                        .doOnError(ex -> System.out.println("out doOnError: " + ex.getMessage()))
                        .doOnCancel(() -> System.out.println("out doOnCancel"));
            }
        };

        tcpServer = TcpServer.create()
                .doOnBind(tcpServerConfig -> {
                    System.out.println("server doOnBind " + tcpServerConfig);
                    linkStatusSink.emitNext(Status.STARTING, EmitFailureHandler.FAIL_FAST);
                })
                .doOnBound(disposableServer -> {
                    System.out.println("server doOnBound " + disposableServer);
                    linkStatusSink.emitNext(Status.CONNECTING, EmitFailureHandler.FAIL_FAST);
                })
                .doOnConnection(tcpConnection -> {
                    System.out.println("server doOnConnection " + tcpConnection);
                    linkStatusSink.emitNext(Status.CONNECTED, EmitFailureHandler.FAIL_FAST);
                    tcpConnection.onDispose().subscribe(null, null, () -> System.out.println("Connection is being disposed"));
                })
                .doOnUnbound(disposableServer -> { 
                    System.out.println("server doOnUnbound " + disposableServer);
                    linkStatusSink.emitNext(Status.STOPPING, EmitFailureHandler.FAIL_FAST);
                })
//                .doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
//                    System.out.println("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
//                })
                .channelGroup(group)
                .childObserve((tcpConnection, newState) -> {
                    // See ConnectionObserver.State
                    // doOnConnection is set, this seems to affect childObserve too as the event is not passed to child observer
                    if (newState == ConnectionObserver.State.CONNECTED) {
                        group.add(tcpConnection.channel());
                        System.out.println("New connection added:" + tcpConnection + ", currently " + group.size());
                    }
                    
                    // There must be a subscriber to inbound stream
                    // If not, disconnection event is not reported
                	System.out.println("childObserve:" + newState + ":" + tcpConnection);
                    if (newState == ConnectionObserver.State.DISCONNECTING) {
                        System.out.println("server childObserve DISCONNECTING");
                        linkStatusSink.emitNext(Status.STOPPING, EmitFailureHandler.FAIL_FAST);
                    }
                })
                .handle(ioHandler)
                .host(this.host)
                .port(this.port)
                .wiretap(this.logging)
                .noSSL();

        
//        server = tcpServer.handle(ioHandler).bindNow();
//        server.onDispose().block();
        
//        tcpServer.handle(ioHandler).bindUntilJavaShutdown(Duration.ofMinutes(1), s -> System.out.println("Started"));
//        tcpServer.handle(ioHandler).bind().subscribe(s -> {
//            this.server = s;
//            System.out.println("server bind returned");
//        }, ex -> {
//            System.out.println("server bind error: " + ex.getMessage());
//        }, () -> {
//            System.out.println("server bind done");
//        });
	}
	
	@Override
	public Mono<Void> start() {
		Sinks.One<Void> sink = Sinks.one();
		
		tcpServer.bind().subscribe(s -> {
			this.disposableServer = s;
			sink.tryEmitEmpty();
            System.out.println("server bind returned");
		}, ex -> {
			sink.tryEmitError(ex);
			System.out.println("server bind error: " + ex.getMessage());
		});
		
		return sink.asMono();
	}

	@Override
	public Mono<Void> stop() {
		group.disconnect().addListener(new ChannelGroupFutureListener() {

			@Override
			public void operationComplete(ChannelGroupFuture future) throws Exception {
				System.out.println("All connections are closed, so disposing server now");
				disposableServer.dispose();
			}
		});
		
		return disposableServer.onDispose();
	}

	@Override
	public Flux<Status> getLinkStatusStream() {
        return this.linkStatusSink.asFlux()
        		.publishOn(Schedulers.boundedElastic())
        		.doOnSubscribe(sub -> {
        			System.out.println("A new LinkStatus subscriber! " + sub);
        		})
        		.log();

	}

	@Override
	public Flux<ByteBuffer> getInboundMessageStream() {
		return this.linkInboundSink.asFlux()
				.publishOn(Schedulers.boundedElastic())
        		.doOnSubscribe(sub -> {
        			System.out.println("A new Inbound subscriber! " + sub);
        		})
				.log();
	}
}
