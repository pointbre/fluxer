package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.time.Duration;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.AsyncerUtil;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.ConnectionObserver;
import reactor.netty.tcp.TcpServer;

public class TcpServerFluxer extends AbstractTcpFluxer implements ServerFluxer<byte[]> {

	public TcpServerFluxer(String localIPAddress, Integer localPort) throws Exception {
		super(localIPAddress, localPort);
	}

	@Override
	public Mono<Fluxer.RequestResult> disconnect(Fluxer.EndPoint remote) {
		Sinks.One<Fluxer.RequestResult> resultSink = Sinks.one();

		// TODO: Implement this

		return resultSink.asMono();
	}

	@Override
	protected TaskResult<Boolean> createTcpConnection() {
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
					emitLink(connection.channel().id().asLongText(), Fluxer.Link.State.CONNECTED,
							new Fluxer.EndPoint(local.getAddress().getHostAddress(), local.getPort()),
							new Fluxer.EndPoint(remote.getAddress().getHostAddress(), remote.getPort()));
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
						emitLink(connection.channel().id().asLongText(),
								Fluxer.Link.State.DISCONNECTED,
								new Fluxer.EndPoint(local.getAddress().getHostAddress(), local.getPort()),
								new Fluxer.EndPoint(remote.getAddress().getHostAddress(), remote.getPort()));
						emitLog(Level.INFO, "The connection is closed: " + connection.channel());
					}
				})
				.handle(handler)
				.host(getIpAddress())
				.port(getPort())
				.wiretap(true)
				.noSSL();

		Boolean result = null;
		String description = null;
		try {
			disposableChannel = tcpServer.bindNow(Duration.ofSeconds(5));
			result = Boolean.TRUE;
			description = "Successfully started at " + getIpAddress() + ":" + getPort();
		} catch (Exception e) {
			result = Boolean.FALSE;
			description = e.getLocalizedMessage();
		}

		return new TaskResult<>(AsyncerUtil.generateType1UUID(), result, description);
	}
}
