package com.github.pointbre.core;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ConnectionObserver;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;


public class TcpServerLink implements Link {
	
	// start and stop --> stream
	
    private DisposableServer server;
    private TcpServer tcpServer;
    private EventExecutor executor = new DefaultEventExecutor();
    private ChannelGroup group = new DefaultChannelGroup(executor);
    private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> ioHandler;
    private NettyInbound inbound;
    private NettyOutbound outbound;
    
    // Reactor netty's internal streams seem to be closed when connection is terminated
    // So, would be better to create a flux which survives regardless of connection/disconnection    
    // https://hilla.dev/blog/interactive-applications-with-server-push/
    private Many<Status> linkStatusSink;
    private Many<ByteBuffer> linkInboundSink;
    
    

	public TcpServerLink() {
        linkStatusSink = Sinks.many().multicast().<Status>onBackpressureBuffer();
        linkInboundSink = Sinks.many().multicast().<ByteBuffer>onBackpressureBuffer();

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
                    tcpConnection.onDispose().subscribe(null, null, () -> System.out.println("being disposed"));
                })
                .doOnUnbound(disposableServer -> { 
                    System.out.println("server doOnUnbound " + disposableServer);
                    linkStatusSink.emitNext(Status.STOPPING, EmitFailureHandler.FAIL_FAST);
                })

//                .doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
//                    System.out.println("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
//                })
//                .channelGroup(group)
                .childObserve((tcpConnection, newState) -> {
                    // See ConnectionObserver.State
                    // doOnConnection is set, this seems to affect childObserve too as the event is not passed to child observer
//                    if (newState == ConnectionObserver.State.CONNECTED) {
//                        group.add(connection.channel());
//                    }
                    
                    // There must be a subscriber to inbound stream
                    // If not, disconnection event is not reported
                    if (newState == ConnectionObserver.State.DISCONNECTING) {
                        System.out.println("server childObserve DISCONNECTING");
                        linkStatusSink.emitNext(Status.STOPPING, EmitFailureHandler.FAIL_FAST);
                    }
                    System.out.println(newState + ":" + tcpConnection);
                }).host("localhost").port(8422).wiretap(true).noSSL();

        BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> ioHandler = new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {

            @Override
            public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {

                in.withConnection((tcpConnection) -> {
                    System.out.println("withConnection");
                    System.out.println(tcpConnection.channel().localAddress());
                    System.out.println(tcpConnection.channel().remoteAddress());
                }).receive().asByteBuffer()
                        .doOnCancel(() -> System.out.println("in doOnCancel"))
                        .doOnComplete(() -> System.out.println("in doOnComplete"))
//                .doOnDiscard(null, null)
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
        
        tcpServer.handle(ioHandler).bindNow();
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
	public Flux<Status> getLinkStatusStream() {
		// TODO Auto-generated method stub
        return this.linkStatusSink.asFlux().publishOn(Schedulers.boundedElastic()).log();

	}

	@Override
	public Flux<ByteBuffer> getInboundStream() {
		// TODO Auto-generated method stub
		return this.linkInboundSink.asFlux().publishOn(Schedulers.boundedElastic()).log();
	}

	@Override
	public void setOutboundStream(Flux<ByteBuffer> outboundStream) {
		// TODO Auto-generated method stub
		
	}

}
