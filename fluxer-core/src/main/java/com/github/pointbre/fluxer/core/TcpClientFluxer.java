package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.time.Duration;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.AsyncerUtil;

import io.netty.channel.ChannelOption;
import reactor.netty.tcp.TcpClient;

public class TcpClientFluxer extends AbstractTcpFluxer implements ClientFluxer<byte[]> {

	public TcpClientFluxer(String remoteIPAddress, Integer remotePort) throws Exception {
		super(remoteIPAddress, remotePort);
	}

	@Override
	protected Result<Boolean> createTcpConnection() {
		TcpClient tcpClient = TcpClient.create()
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.doOnResolve(connection -> {
				})
				.doAfterResolve((connection, socketAddress) -> {
				})
				.doOnResolveError((connection, error) -> {
				})
				.doOnConnect(config -> {
				})
				.doOnConnected(connection -> {
					channelGroup.add(connection.channel());
					InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
					InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
					emitLink(connection.channel().id().asLongText(), Link.State.CONNECTED,
							new EndPoint(local.getAddress().getHostAddress(), Integer.valueOf(local.getPort())),
							new EndPoint(remote.getAddress().getHostAddress(), Integer.valueOf(remote.getPort())));
					emitLog(Level.INFO, "A new connection is established: " + connection.channel());
				})
				.doOnDisconnected(connection -> {
					InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
					InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
					emitLink(connection.channel().id().asLongText(), Link.State.DISCONNECTED,
							new EndPoint(local.getAddress().getHostAddress(),
									Integer.valueOf(local.getPort())),
							new EndPoint(remote.getAddress().getHostAddress(),
									Integer.valueOf(remote.getPort())));
					emitLog(Level.INFO, "The existing connection is terminated: " +
							connection.channel());
				})
				.observe((tcpConnection, newState) -> {
				})
				.handle(handler)
				.host(getIpAddress())
				.port(getPort())
				.wiretap(true)
				.noSSL();

		Boolean result = null;
		String description = null;

		try {
			disposableChannel = tcpClient.connectNow(Duration.ofSeconds(5));
			result = Boolean.TRUE;
			description = "Successfully started at " + getIpAddress() + ":" + getPort();
		} catch (Exception e) {
			result = Boolean.FALSE;
			description = e.getLocalizedMessage();
		}

		return new TaskResult<>(AsyncerUtil.generateType1UUID(), result, description);
	}
}
