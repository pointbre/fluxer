package com.github.pointbre.fluxer.core;

import java.io.IOException;

import com.github.pointbre.fluxer.core.Fluxer.Event;
import com.github.pointbre.fluxer.core.Fluxer.Result;

import io.netty.channel.ChannelOption;
import io.netty.handler.flush.FlushConsolidationHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.One;
import reactor.netty.tcp.TcpClient;

@Slf4j
public class TcpClientFluxer extends TcpFluxer {

    public TcpClientFluxer(String remoteIPAddress, Integer remotePort) throws Exception {
	super(remoteIPAddress, remotePort);
    }

    @Override
    protected void createTcpConnection() {
	TcpClient tcpClient = TcpClient.create()
		.option(ChannelOption.SO_KEEPALIVE, true)
		.option(ChannelOption.TCP_NODELAY, true)
		.doOnResolve(connection -> {
//		    log.debug("client doOnResolve: " + connection);
		})
		.doAfterResolve((connection, socketAddress) -> {
//		    log.debug("client doAfterResolve: " + connection + ", " + socketAddress);
		})
		.doOnResolveError((connection, error) -> {
//		    log.debug("client doOnResolveError: " + connection + ", " + error);
		})
		.doOnConnect(config -> {
//		    log.debug("client doOnConnect: " + config);
		})
		.doOnConnected(connection -> {
//		    log.debug("client doOnConnected: " + connection);
		    // Add handlers here
//					connection.addHandlerFirst(new FlushConsolidationHandler(1, true));					
		    group.add(connection.channel());
//		    log.debug("New connection added:" + connection + ", currently " + group.size());

		    emitLink(connection, Link.State.CONNECTED);
		})
		.doOnDisconnected(connection -> {
//		    System.out.println("client doOnDisconnected: " + connection);
//		    log.debug("client doOnDisconnected: " + connection);
		    emitLink(connection, Link.State.DISCONNECTED);
		})
		.observe((tcpConnection, newState) -> {
//		    System.out.println("client observe:" + newState + ":" + tcpConnection);
//		    log.debug("client observe:" + newState + ":" + tcpConnection);
		})
		.handle(handler)
		.host(getIpAddress())
		.port(getPort())
		.wiretap(true)
		.noSSL();

	tcpClient.connect()
		.subscribe(connection -> {
		    this.disposableChannel = connection;
		    System.out.println("client connect() returned, will send PROCESSED");
//		    emitStatus(State.STARTED);
		    sendEventToStateMachine(Event.PROCESSED);
		    Sinks.One<Result> resultSink = getResultSink(Event.START_REQUESTED);
		    if (resultSink != null) {
			resultSink.tryEmitValue(Result.PROCESSED);
		    }
		    removeResultSink(Event.START_REQUESTED);
		}, ex -> {
//					emitStartException(resultSink, "Failed to connect: " + ex.getLocalizedMessage());
		    System.out.println("client connect() error: " + ex.getMessage());
//		    emitStatus(State.STOPPED);
		    sendEventToStateMachine(Event.FAILED);
		    Sinks.One<Result> resultSink = getResultSink(Event.START_REQUESTED);
		    if (resultSink != null) {
			resultSink.tryEmitValue(Result.FAILED);
		    }
		    removeResultSink(Event.START_REQUESTED);
		});
    }
}
