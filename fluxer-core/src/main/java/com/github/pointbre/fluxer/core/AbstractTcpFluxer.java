package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.reactivestreams.Publisher;
import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.AsyncerUtil;
import com.github.pointbre.asyncer.core.SequentialFAETaskExecutorImpl;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import reactor.netty.DisposableChannel;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.util.annotation.NonNull;

public abstract class AbstractTcpFluxer extends AbstractFluxer<byte[]> implements TcpFluxer<byte[]> {

	private static final int MAX_WAIT = 5;
	private final String ipAddress;
	private final Integer port;

	protected BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> tcpConnectionHandler;
	protected DisposableChannel disposableChannel;
	protected EventExecutor eventExecutor;
	protected ChannelGroup channelGroup;

	protected AbstractTcpFluxer(String ipAddress, Integer port) throws Exception {
		super();

		// FIXME Probably better to use custom exception
		if (ipAddress == null || ipAddress.isBlank()) {
			Exception exception = new IllegalArgumentException("Invalid ip address: must not be null or blank");
			emitLog(Level.ERROR, "The given ip address is invalid", exception);
			throw exception;
		}
		if (port <= 0 || port >= 65536) {
			Exception exception = new IllegalArgumentException("Invalid port: must be >= 1 and <= 65535");
			emitLog(Level.ERROR, "The given port number is invalid", exception);
			throw exception;
		}

		this.ipAddress = ipAddress;
		this.port = port;
	}

	@Override
	public String getIpAddress() {
		return ipAddress;
	}

	@Override
	public Integer getPort() {
		return port;
	}

	protected abstract Result<Boolean> createTcpConnection();

	@Override
	protected Result<Boolean> processStartRequest(@NonNull State state, @NonNull Event<byte[]> event) {

		List<BiFunction<State, Event<byte[]>, Result<Boolean>>> tasksToExecute = new ArrayList<>();

		tasksToExecute.add((s, e) -> {
			tcpConnectionHandler = createTcpConnectionHandler();
			eventExecutor = new DefaultEventExecutor();
			channelGroup = new DefaultChannelGroup(eventExecutor);
			return prepareResult(true, "Finished creating handler, event executor and channel group");
		});

		tasksToExecute.add((s, e) -> createTcpConnection());

		return runTasks(state, event, tasksToExecute);
	}

	@Override
	protected Result<Boolean> processStopRequest(@NonNull State state, @NonNull Event<byte[]> event) {

		List<BiFunction<State, Event<byte[]>, Result<Boolean>>> tasksToExecute = new ArrayList<>();

		tasksToExecute.add((s, e) -> {
			String log = "";
			if (disposableChannel != null) {
				try {
					disposableChannel.dispose();
					disposableChannel.onDispose().block();
				} catch (Exception ex) {
					//
				}
				log = "Closed the channel";
			} else {
				log = "No need of closing channel as it's null";
			}
			return prepareResult(true, log);
		});

		tasksToExecute.add((s, e) -> {
			String log = "";
			if (channelGroup != null) {
				try {
					var future = channelGroup.disconnect();
					future.awaitUninterruptibly();
				} catch (Exception ex) {
					//
				}
				log = "Disconnected the channel group";
			} else {
				log = "No need of disconnecting group as it's null";
			}
			return prepareResult(true, log);
		});

		tasksToExecute.add((s, e) -> {
			String log = "";
			if (channelGroup != null) {
				try {
					var future = channelGroup.close();
					future.awaitUninterruptibly();
				} catch (Exception ex) {
					//
				}
				log = "Closed the channel group";
			} else {
				log = "No need of closing channel as it's null";
			}
			return prepareResult(true, log);
		});

		tasksToExecute.add((s, e) -> {
			String log = "";
			if (eventExecutor != null) {
				try {
					var future = eventExecutor.shutdownGracefully();
					future.awaitUninterruptibly();
				} catch (Exception ex) {
					//
				}
				log = "Closed the event executor";
			} else {
				log = "No need of closing executor as it's null";
			}
			return prepareResult(true, log);
		});

		tasksToExecute.add((s, e) -> {
			disposableChannel = null;
			channelGroup = null;
			eventExecutor = null;
			return prepareResult(true, "Link related resources are reset to null");
		});

		return runTasks(state, event, tasksToExecute);
	}

	@Override
	protected Result<Boolean> processSendRequest(@NonNull State state, @NonNull Event<byte[]> event) {

		List<BiFunction<State, Event<byte[]>, Result<Boolean>>> tasksToExecute = new ArrayList<>();

		tasksToExecute.add((s, e) -> {
			final var message = event.getMessage();
			final var remote = event.getRemote();
			if (message == null || message.length == 0) {
				return prepareResult(false, "The given message is null or empty");
			}
			if (remote == null) {
				return prepareResult(false, "The given remote is null");
			}
			if (channelGroup == null) {
				return prepareResult(false, "Currently no link is established");
			}
			final var channelFound = searchChannel(remote);
			if (channelFound == null) {
				return prepareResult(false, "No matching established link is found: " + remote);
			}

			final var localAddress = (InetSocketAddress) channelFound.localAddress();
			final var local = new EndPoint(localAddress.getAddress().getHostAddress(), localAddress.getPort());
			final var writeResult = channelFound.writeAndFlush(Unpooled.wrappedBuffer(message))
					.awaitUninterruptibly();
			String log = "";
			if (writeResult.isSuccess()) {
				log = "A new outbound message to " + remote + ": " + Hex.encodeHexString(message);
				emitMessage(Message.Type.OUTBOUND, local, remote, message);
			} else {
				log = "Failed to send a message to " + remote + " due to " + writeResult.cause().getLocalizedMessage();
			}
			return prepareResult(writeResult.isSuccess(), log);
		});

		return runTasks(state, event, tasksToExecute);

	}

	protected Result<Boolean> prepareResult(boolean result, String message) {
		emitLog(result ? Level.INFO : Level.ERROR, message);
		return new Result<>(AsyncerUtil.generateType1UUID(), Boolean.valueOf(result), message);
	}

	private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> createTcpConnectionHandler() {
		return (in, out) -> {
			in.receive().asByteArray().doOnNext(buf -> in.withConnection(connection -> {
				InetSocketAddress localAddress = (InetSocketAddress) connection.channel().localAddress();
				EndPoint local = new EndPoint(localAddress.getAddress().getHostAddress(), localAddress.getPort());
				InetSocketAddress remoteAddress = (InetSocketAddress) connection.channel().remoteAddress();
				EndPoint remote = new EndPoint(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort());
				emitMessage(Message.Type.INBOUND, local, remote, buf);
				emitLog(Level.INFO, "A new inbound message from " + remote + ": " + Hex.encodeHexString(buf));
			})).subscribe();

			return out.neverComplete();
		};
	}

	private Channel searchChannel(@NonNull EndPoint remote) {
		Iterator<Channel> channelIterator = channelGroup.iterator();
		boolean linkFound = false;
		Channel channelToCheck = null;
		while (channelIterator.hasNext()) {
			channelToCheck = channelIterator.next();
			if (isSameLink(channelToCheck, remote)) {
				linkFound = true;
				break;
			}
		}
		final Channel channelFound;
		if (!linkFound) {
			channelFound = null;
		} else {
			channelFound = channelToCheck;
		}
		return channelFound;
	}

	private Result<Boolean> runTasks(State state, Event<byte[]> event,
			List<BiFunction<State, Event<byte[]>, Result<Boolean>>> tasksToExecute) {
		TaskExecutor<State, State.Type, Event<byte[]>, Event.Type, Boolean> taskExecutor = new SequentialFAETaskExecutorImpl<>();
		try {
			List<Result<Boolean>> taskResults = taskExecutor.run(state, event, tasksToExecute, Duration.ofSeconds(5));
			boolean allSuccessfullyDone = taskResults.stream().allMatch(tr -> tr.getValue().booleanValue())
					&& taskResults.size() == tasksToExecute.size();
			String description = taskResults.stream().map(Result::getDescription).collect(Collectors.joining(","));
			return new Result<>(AsyncerUtil.generateType1UUID(), Boolean.valueOf(allSuccessfullyDone), description);
		} finally {
			try {
				taskExecutor.close();
			} catch (Exception e) {
				//
			}
		}
	}

	private boolean isSameLink(@NonNull Channel channel, @NonNull EndPoint remote) {
		InetSocketAddress channelRemoteAddress = (InetSocketAddress) channel.remoteAddress();
		return remote.getIpAddress().equals(channelRemoteAddress.getAddress().getHostAddress())
				&& remote.getPort().equals(Integer.valueOf(channelRemoteAddress.getPort()));
	}

}
