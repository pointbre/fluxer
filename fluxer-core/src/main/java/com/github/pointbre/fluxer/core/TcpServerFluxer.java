package com.github.pointbre.fluxer.core;

import org.springframework.statemachine.StateMachineEventResult.ResultType;

import com.github.pointbre.fluxer.core.Fluxer.Result;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import reactor.netty.ConnectionObserver;
import reactor.netty.tcp.TcpServer;

@Slf4j
public class TcpServerFluxer extends TcpFluxer {

    public TcpServerFluxer(String localIPAddress, Integer localPort) throws Exception {
	super(localIPAddress, localPort);
    }

    @Override
    protected void createTcpConnection() {
	TcpServer tcpServer = TcpServer.create()
		.option(ChannelOption.SO_REUSEADDR, true)
		.childOption(ChannelOption.TCP_NODELAY, true)
		.childOption(ChannelOption.SO_KEEPALIVE, true)
		.childOption(ChannelOption.SO_LINGER, 0)
		.doOnBind(tcpServerConfig -> {
//		    System.out.println("server doOnBind " + tcpServerConfig);
		})
		.doOnBound(disposableServer -> {
//		    System.out.println("server doOnBound " + disposableServer);
		})
		.doOnConnection(connection -> {
//		    System.out.println("server doOnConnection " + connection);

		    // Add handlers here
		    // connection.addHandlerFirst(new FlushConsolidationHandler(1, true));

		    group.add(connection.channel());
//		    System.out.println("New connection added:" + connection + ", currently " + group.size());
		    emitLink(connection, Link.State.CONNECTED);
		})
		.doOnUnbound(disposableServer -> {
//		    System.out.println("server doOnUnbound " + disposableServer);
		})
		.doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
//		    System.out.println("server doOnChannelInit " + connectionObserver + "," + channel + "," + remoteAddress);
		})
		.channelGroup(group).childObserve((connection, newState) -> {
		    // See ConnectionObserver.State
		    // If doOnConnection is set, this seems to affect childObserve too, as the event
		    // is not passed to child observer

		    // There must be a subscriber to inbound stream.
		    // If not, disconnection event is not reported.
//		    System.out.println("childObserve:" + newState + ":" + connection);
		    if (newState == ConnectionObserver.State.DISCONNECTING) {
//			System.out.println("server childObserve DISCONNECTING");
//			System.out.println("server childObserve DISCONNECTING");
			emitLink(connection, Link.State.DISCONNECTED);
		    }
		})
		.handle(handler)
		.host(getIpAddress())
		.port(getPort())
		.wiretap(true)
		.noSSL();

	tcpServer.bind()
		.subscribe(disposableServer -> {
		    disposableChannel = disposableServer;
		    System.out.println("server bind() returned");
//		    emitStatus(State.STARTED);
		    // TODO call subscribe() like processStopRequest()
		    sendEvent(Event.PROCESSED);
		    Sinks.One<Result> resultSink = getResultSink(Event.START_REQUESTED);
		    if (resultSink != null) {
			resultSink.tryEmitValue(new Result(Result.Type.PROCESSED, "TcpServer successfully started at " + getIpAddress() + ":" + getPort()));
		    }
		    removeResultSink(Event.START_REQUESTED);
		}, ex -> {
		    System.out.println("server bind() error: " + ex.getMessage());
//		    emitStatus(State.STOPPED);
		    // TODO call subscribe() like processStopRequest()
		    sendEvent(Event.FAILED);
		    Sinks.One<Result> resultSink = getResultSink(Event.START_REQUESTED);
		    if (resultSink != null) {
			resultSink.tryEmitValue(new Result(Result.Type.FAILED, "TcpServer failed to start at " + getIpAddress() + ":" + getPort() + ", " +ex.getLocalizedMessage()));
		    }
		    removeResultSink(Event.START_REQUESTED);
		});

    }
}
