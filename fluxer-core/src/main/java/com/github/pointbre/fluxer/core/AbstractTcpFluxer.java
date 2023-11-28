package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.AsyncerUtil;
import com.github.pointbre.asyncer.core.SequentialFAETaskExecutor;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableChannel;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.util.annotation.NonNull;

public abstract class AbstractTcpFluxer extends AbstractFluxer<byte[]> implements TcpFluxer<byte[]> {

	private static final int MAX_WAIT = 5;
	private final String ipAddress;
	private final Integer port;

	protected BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
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
	public Mono<RequestResult> start() {
		return triggerEvent(State.Event.START);
	}

	@Override
	public Mono<RequestResult> stop() {
		return triggerEvent(State.Event.STOP);
	}

	@Override
	public Mono<RequestResult> send(byte[] message, EndPoint remote) {
		Sinks.One<RequestResult> resultSink = Sinks.one();

		// TODO Should check if the current state is STARTED???
		// TODO Can I use asyncer for this? Hmm, not sure how to pass arguments
		if (channelGroup != null) {
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
			if (channelFound != null) {
				final InetSocketAddress localAddress = (InetSocketAddress) channelFound.localAddress();
				final EndPoint local = new EndPoint(localAddress.getAddress().getHostAddress(), localAddress.getPort());
				channelFound.writeAndFlush(Unpooled.wrappedBuffer(message))
						.addListener(new ChannelFutureListener() {
							@Override
							public void operationComplete(ChannelFuture future) throws Exception {
								String log = "Successfully sent to " + remote + ":" + ByteBufUtil.hexDump(message);
								resultSink.tryEmitValue(
										new RequestResult(AsyncerUtil.generateType1UUID(), State.Event.SEND,
												Boolean.TRUE, log));
								emitMessage(Message.Type.OUTBOUND, local, remote, message);
								emitLog(Level.INFO, log);
							}
						});
			} else {
				String log = "Failed to send a message as matching link is not found: " + remote;
				resultSink.tryEmitValue(
						new RequestResult(AsyncerUtil.generateType1UUID(), State.Event.SEND, Boolean.FALSE, log));
				emitLog(Level.ERROR, log);
			}
		} else {
			String log = "Failed to send a message as no link is not found: " + remote;
			resultSink.tryEmitValue(
					new RequestResult(AsyncerUtil.generateType1UUID(), State.Event.SEND, Boolean.FALSE, log));
			emitLog(Level.ERROR, log);
		}

		return resultSink.asMono();
	}

	@Override
	public String getIpAddress() {
		return ipAddress;
	}

	@Override
	public Integer getPort() {
		return port;
	}

	protected abstract TaskResult<Boolean> createTcpConnection();

	protected TaskResult<Boolean> processStartRequest() {

		System.out.println("Running processStartRequest");

		List<Callable<TaskResult<Boolean>>> tasksToExecute = new ArrayList<>();
		tasksToExecute.add(() -> {
			handler = createHandler();
			eventExecutor = new DefaultEventExecutor();
			channelGroup = new DefaultChannelGroup(eventExecutor);
			emitLog(Level.INFO, "Finished creating handler, event executor and channel group");
			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE,
					"Finished creating handler, event executor and channel group");
		});
		tasksToExecute.add(() -> {
			emitLog(Level.INFO, "Finished creating a tcp connection");
			return createTcpConnection();
		});

		return runTasks(tasksToExecute);
	}

	protected TaskResult<Boolean> processStopRequest() {
		System.out.println("Running processStopRequest");
		List<Callable<TaskResult<Boolean>>> tasksToExecute = new ArrayList<>();
		tasksToExecute.add(() -> {
			if (disposableChannel != null) {
				try {
					disposableChannel.dispose();
					disposableChannel.onDispose().block();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				emitLog(Level.INFO, "Closed the channel");
			} else {
				emitLog(Level.INFO, "No need of closing channel as it's null");
			}

			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE,
					"Finished closing disposable channel");
		});

		tasksToExecute.add(() -> {
			if (channelGroup != null) {
				var future = channelGroup.disconnect();
				future.await();
				// final CountDownLatch countDownLatch = new CountDownLatch(1);
				// try {
				// channelGroup.disconnect().addListener(new ChannelGroupFutureListener() {
				// @Override
				// public void operationComplete(ChannelGroupFuture future) throws Exception {
				// countDownLatch.countDown();
				// }
				// });
				// try {
				// countDownLatch.await(MAX_WAIT, TimeUnit.SECONDS);
				// } catch (InterruptedException e) {
				// }
				// } catch (Exception e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// }
				emitLog(Level.INFO, "Disconnected the channel group");
			} else {
				emitLog(Level.INFO, "No need of disconnecting group as it's null");
			}
			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE,
					"Finished disconnecting the channel group");
		});

		tasksToExecute.add(() -> {
			if (channelGroup != null) {
				try {
					var future = channelGroup.close();
					future.await();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				emitLog(Level.INFO, "Closed the channel group");
			} else {
				emitLog(Level.INFO, "No need of closing channel as it's null");
			}
			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE,
					"Finished closing the channel group");
		});

		tasksToExecute.add(() -> {
			if (eventExecutor != null) {
				try {
					var future = eventExecutor.shutdownGracefully();
					future.await();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				emitLog(Level.INFO, "Closed the event executor");
			} else {
				emitLog(Level.INFO, "No need of closing executor as it's null");
			}
			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE,
					"Finished creating handler, event executor and channel group");
		});

		tasksToExecute.add(() -> {
			disposableChannel = null;
			channelGroup = null;
			eventExecutor = null;
			emitLog(Level.INFO, "Link related resources are closed");
			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.TRUE,
					"Finished creating handler, event executor and channel group");
		});

		return runTasks(tasksToExecute);
	}

	private Mono<RequestResult> triggerEvent(State.Event event) {
		final Sinks.One<RequestResult> resultSink = Sinks.one();
		asyncer.fire(AsyncerUtil.generateType1UUID(), event)
				.subscribe(transitionResult -> {
					emitLog(Level.INFO, event + " request processing result: " + transitionResult);
					resultSink.tryEmitValue(new RequestResult(transitionResult.getUuid(), transitionResult.getEvent(),
							transitionResult.getResult(), transitionResult.getDescription()));
				});

		return resultSink.asMono();
	}

	private TaskResult<Boolean> runTasks(List<Callable<TaskResult<Boolean>>> tasksToExecute) {
		TaskExecutor<Boolean> taskExecutor = new SequentialFAETaskExecutor();
		try {
			List<TaskResult<Boolean>> taskResults = taskExecutor.run(tasksToExecute, Duration.ofSeconds(5));
			boolean allSuccessfullyDone = taskResults.stream().allMatch(tr -> tr.getResult().booleanValue())
					&& taskResults.size() == tasksToExecute.size();
			String description = taskResults.stream().map(tr -> tr.getDescription()).collect(Collectors.joining(","));
			return new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.valueOf(allSuccessfullyDone),
					description);
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
		if (remote.getIpAddress().equals(channelRemoteAddress.getAddress().getHostAddress())
				&& remote.getPort().equals(Integer.valueOf(channelRemoteAddress.getPort()))) {
			return true;
		}

		return false;
	}

	private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> createHandler() {
		return new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
			@Override
			public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
				in.receive().asByteArray().doOnNext(buf -> {
					in.withConnection(connection -> {
						InetSocketAddress local = (InetSocketAddress) connection.channel().localAddress();
						InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
						emitMessage(Message.Type.INBOUND,
								new EndPoint(local.getAddress().getHostAddress(), local.getPort()),
								new EndPoint(remote.getAddress().getHostAddress(), remote.getPort()), buf);
						String log = "A new message is received from " + connection.channel().localAddress().toString();
						emitLog(Level.INFO, log);
					});
				}).subscribe();

				return out.neverComplete();
			}
		};
	}

}
