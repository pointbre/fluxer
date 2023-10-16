package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;

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
		})
		.doOnDisconnected(connection -> {
		    InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
		    InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
		    emitLink(connection.channel().id().asLongText(), Link.State.DISCONNECTED,
			    new EndPoint(local.getAddress().getHostAddress(), Integer.valueOf(local.getPort())),
			    new EndPoint(remote.getAddress().getHostAddress(), Integer.valueOf(remote.getPort())));
		})
		.observe((tcpConnection, newState) -> {
		})
		.handle(handler)
		.host(getIpAddress())
		.port(getPort())
		.wiretap(true)
		.noSSL();

	Sinks.One<Result> resultSink = getResultSink(State.Event.START_REQUESTED);
	tcpClient.connect()
		.subscribe(connection -> {
		    this.disposableChannel = connection;
		    sendEvent(State.Event.PROCESSED)
			    .subscribe(results -> {
				if (!isEventAccepted(results)) {
				    resultSink.tryEmitValue(new Result(Result.Type.FAILED,
					    "The request can't be accepted as it's currently " + getFluxerState()));
				} else {
				    resultSink.tryEmitValue(new Result(Result.Type.PROCESSED,
					    "TcpClient successfully started at " + getIpAddress() + ":" + getPort()));
				}
				removeResultSink(State.Event.START_REQUESTED);
			    }, error -> {
				resultSink.tryEmitValue(new Result(Result.Type.FAILED, error.getLocalizedMessage()));
				removeResultSink(State.Event.START_REQUESTED);
			    });
		}, ex -> {
		    sendEvent(State.Event.FAILED)
			    .subscribe(results -> {
				if (!isEventAccepted(results)) {
				    resultSink.tryEmitValue(new Result(Result.Type.FAILED,
					    "The request can't be accepted as it's currently " + getFluxerState()));
				} else {
				    resultSink.tryEmitValue(new Result(Result.Type.FAILED,
					    "TcpClient failed to start at "
						    + getIpAddress() + ":" + getPort() + ", "
						    + ex.getLocalizedMessage()));
				}
				removeResultSink(State.Event.START_REQUESTED);
			    }, error -> {
				resultSink.tryEmitValue(new Result(Result.Type.FAILED, error.getLocalizedMessage()));
				removeResultSink(State.Event.START_REQUESTED);
			    });
		});
    }
}
