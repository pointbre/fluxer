package com.github.pointbre.fluxer.core;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks.One;
import reactor.netty.ConnectionObserver;
import reactor.netty.tcp.TcpServer;

@Slf4j
public class TcpServerFluxer extends TcpFluxer {

	public TcpServerFluxer(String localIPAddress, Integer localPort) {
		super(localIPAddress, localPort);
	}

	@Override
	protected void createTcpConnection(One<Void> resultSink) {
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
				.host(ipAddress)
				.port(port)
				.wiretap(true)
				.noSSL();

		tcpServer.bind()
				.subscribe(disposableServer -> {
					resultSink.tryEmitEmpty();
					this.disposableChannel = disposableServer;
					log.debug("server bind() returned");
					emitStatus(Status.STARTED);
				}, ex -> {
					resultSink.tryEmitError(ex);
					log.debug("server bind() error: " + ex.getMessage());
					emitStatus(Status.STOPPED);
				});
		
	}
}
