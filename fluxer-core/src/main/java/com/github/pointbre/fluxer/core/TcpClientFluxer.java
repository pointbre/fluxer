package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;

import org.slf4j.event.Level;

import com.github.pointbre.fluxer.core.Fluxer.RequestResult;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Sinks;
import reactor.netty.tcp.TcpClient;

public class TcpClientFluxer extends AbstractTcpFluxer implements ClientFluxer<byte[]> {

	public TcpClientFluxer(String remoteIPAddress, Integer remotePort) throws Exception {
		super(remoteIPAddress, remotePort);
	}

	@Override
	protected void createTcpConnection() {
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
					group.add(connection.channel());
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
							new EndPoint(local.getAddress().getHostAddress(), Integer.valueOf(local.getPort())),
							new EndPoint(remote.getAddress().getHostAddress(), Integer.valueOf(remote.getPort())));
					emitLog(Level.INFO, "The existing connection is terminated: " + connection.channel());
				})
				.observe((tcpConnection, newState) -> {
				})
				.handle(handler)
				.host(getIpAddress())
				.port(getPort())
				.wiretap(true)
				.noSSL();

		Sinks.One<RequestResult> resultSink = getResultSink(State.Event.START);
		tcpClient.connect()
				.subscribe(connection -> {
					this.disposableChannel = connection;
					sendEvent(State.Event.PROCESSED)
							.subscribe(results -> {
								if (!isEventAccepted(results)) {
									String log = "PROCESSED event wasn't accepted as it's currently "
											+ getFluxerMachineState();
									resultSink.tryEmitValue(new RequestResult(RequestResult.Type.FAILED, log));
									emitLog(Level.ERROR, log);
								} else {
									String log = "TcpClient successfully started at " + getIpAddress() + ":"
											+ getPort();
									resultSink.tryEmitValue(new RequestResult(RequestResult.Type.PROCESSED, log));
									emitLog(Level.INFO, log);
								}
								removeResultSink(State.Event.START_REQUESTED);
							}, error -> {
								resultSink.tryEmitValue(
										new RequestResult(RequestResult.Type.FAILED, error.getLocalizedMessage()));
								removeResultSink(State.Event.START_REQUESTED);
								emitLog(Level.ERROR, "Failed to send PROCESSED event:" + error.getLocalizedMessage(),
										error);
							});
				}, ex -> {
					sendEvent(State.Event.FAILED)
							.subscribe(results -> {
								if (!isEventAccepted(results)) {
									String log = "PROCESSED event wasn't accepted as it's currently "
											+ getFluxerMachineState();
									resultSink.tryEmitValue(new RequestResult(RequestResult.Type.FAILED, log));
									emitLog(Level.ERROR, log);
								} else {
									String log = "TcpClient failed to start at " + getIpAddress() + ":" + getPort();
									resultSink.tryEmitValue(new RequestResult(RequestResult.Type.FAILED,
											log + ":" + getPort() + ", " + ex.getLocalizedMessage()));
									emitLog(Level.ERROR, log, ex);
								}
								removeResultSink(State.Event.START_REQUESTED);
							}, error -> {
								resultSink.tryEmitValue(
										new RequestResult(RequestResult.Type.FAILED, error.getLocalizedMessage()));
								removeResultSink(State.Event.START_REQUESTED);
								emitLog(Level.ERROR, "Failed to send FAILED event:" + error.getLocalizedMessage(),
										error);
							});
				});
	}
}
