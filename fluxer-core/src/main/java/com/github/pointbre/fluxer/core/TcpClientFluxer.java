package com.github.pointbre.fluxer.core;

import io.netty.channel.ChannelOption;
import io.netty.handler.flush.FlushConsolidationHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks.One;
import reactor.netty.tcp.TcpClient;

@Slf4j
public class TcpClientFluxer extends TcpFluxer {

	public TcpClientFluxer(String remoteIPAddress, Integer remotePort) {
		super(remoteIPAddress, remotePort);
	}

	@Override
	protected void createTcpConnection(One<Void> resultSink) {
		TcpClient tcpClient = TcpClient.create()
				.option(ChannelOption.TCP_NODELAY, true)
				.doOnResolve(connection -> {
					log.debug("client doOnResolve: " + connection);
				})
				.doAfterResolve((connection, socketAddress) -> {
					log.debug("client doAfterResolve: " + connection + ", " + socketAddress);
				})
				.doOnResolveError((connection, error) -> {
					log.debug("client doOnResolveError: " + connection + ", " + error);
				})
				.doOnConnect(config -> {
					log.debug("client doOnConnect: " + config);					
				})
				.doOnConnected(connection -> {
					log.debug("client doOnConnected: " + connection);
					
//					connection.addHandlerFirst(new FlushConsolidationHandler(0, true));					
					group.add(connection.channel());
					log.debug("New connection added:" + connection + ", currently " + group.size());
					
					emitLink(connection, Link.Status.CONNECTED);
				})
				.doOnDisconnected(connection -> {
					System.out.println("client doOnDisconnected: " + connection);
					log.debug("client doOnDisconnected: " + connection);
					emitLink(connection, Link.Status.DISCONNECTED);
				})
				.observe((tcpConnection, newState) -> {
					System.out.println("client observe:" + newState + ":" + tcpConnection);
					log.debug("client observe:" + newState + ":" + tcpConnection);
				})
				.handle(handler)
				.host(ipAddress)
				.port(port)
				.wiretap(true)
				.noSSL();

		tcpClient.connect()
				.subscribe(connection -> {
					resultSink.tryEmitEmpty();
					this.disposableChannel = connection;
					log.debug("client connect() returned");
					emitStatus(Status.STARTED);
				}, ex -> {
					resultSink.tryEmitError(ex);
					log.debug("client connect() error: " + ex.getMessage());
					emitStatus(Status.STOPPED);
				});
	}
}
