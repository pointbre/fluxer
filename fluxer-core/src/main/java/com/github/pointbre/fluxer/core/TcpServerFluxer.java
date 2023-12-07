package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.time.Duration;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer.Result;

import io.netty.channel.ChannelOption;
import reactor.netty.ConnectionObserver;
import reactor.netty.tcp.TcpServer;

public class TcpServerFluxer extends AbstractTcpFluxer implements ServerFluxer<byte[]> {

	public TcpServerFluxer(String localIPAddress, Integer localPort) throws Exception {
		super(localIPAddress, localPort);
	}

	@Override
	protected Result<Boolean> createTcpConnection() {
		TcpServer tcpServer = TcpServer.create()
				.option(ChannelOption.SO_REUSEADDR, true)
				.childOption(ChannelOption.TCP_NODELAY, true)
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				.childOption(ChannelOption.SO_LINGER, 0)
				.doOnBind(tcpServerConfig -> {
					emitLog(Level.INFO, "Server's binding started: " + tcpServerConfig);
				})
				.doOnBound(disposableServer -> {
					emitLog(Level.INFO, "Server's binding done: " + disposableServer);
				})
				.doOnConnection(connection -> {
					channelGroup.add(connection.channel());
					InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
					InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
					emitLink(Link.Type.CONNECTED, connection.channel().id().asLongText(),
							new EndPoint(local.getAddress().getHostAddress(), local.getPort()),
							new EndPoint(remote.getAddress().getHostAddress(), remote.getPort()));
					emitLog(Level.INFO, "A new connection is established: " + connection.channel());
				})
				.doOnUnbound(disposableServer -> {
					emitLog(Level.INFO, "Server's unbinding done: " + disposableServer);
				})
				.doOnChannelInit((connectionObserver, channel, remoteAddress) -> {
					emitLog(Level.INFO, "Server's channel is initialized");
				})
				.channelGroup(channelGroup).childObserve((connection, newState) -> {
					// See ConnectionObserver.State
					// - If doOnConnection is set, this seems to affect childObserve too as the
					// event is not passed to child observer

					// There must be a subscriber to inbound stream.
					// - If not, disconnection event is not reported.
					if (newState == ConnectionObserver.State.DISCONNECTING) {
						InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
						InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
						emitLink(Link.Type.DISCONNECTED, connection.channel().id().asLongText(),
								new EndPoint(local.getAddress().getHostAddress(), local.getPort()),
								new EndPoint(remote.getAddress().getHostAddress(), remote.getPort()));
						emitLog(Level.INFO, "The connection is closed: " + connection.channel());
					}
				})
				.handle(tcpConnectionHandler)
				.host(getIpAddress())
				.port(getPort())
				.wiretap(true)
				.noSSL();

		boolean result;
		String description = "";

		try {
			disposableChannel = tcpServer.bindNow(Duration.ofSeconds(5));
			result = true;
			description = "Successfully started a tcp client at " + getIpAddress() + ":" + getPort();
		} catch (Exception e) {
			result = false;
			description = "Failed to start a tcp client at " + getIpAddress() + ":" + getPort() + " due to "
					+ e.getLocalizedMessage();
		}

		return prepareResult(result, description);
	}
}
