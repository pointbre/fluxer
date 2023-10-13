package com.github.pointbre.fluxer.core;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.StateMachineEventResult.ResultType;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.config.StateMachineBuilder.Builder;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.transition.Transition;

import com.github.pointbre.fluxer.core.Fluxer.Message.Type;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.DisposableChannel;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.util.concurrent.Queues;

@Slf4j
public abstract class TcpFluxer implements Fluxer {

    private final String ipAddress;
    private final Integer port;

    private Many<State> stateSink;
    private Flux<State> stateFlux;

    private Many<Link> linkSink;
    private Flux<Link> linkFlux;

    private Many<Message> messageSink;
    private Flux<Message> messageFlux;

    private Builder<State, Event> fluxerMachineBuilder;
    private StateMachine<State, Event> fluxerMachine;

    protected BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> handler;
    protected DisposableChannel disposableChannel;
    protected EventExecutor executor;
    protected ChannelGroup group;

    abstract protected void createTcpConnection();

    public TcpFluxer(String ipAddress, Integer port) throws FluxerException {
	
	if (ipAddress == null || ipAddress.isBlank()) {
	    log.error("Invalid ip address: must not be null or blank");
	    throw new FluxerException("Invalid ip address: must not be null or blank");
	}

	if (port <= 0 || port >= 65536) {
	    log.error("Invalid port: must be >= 1 and <= 65535");
	    throw new FluxerException("Invalid port: must be >= 1 and <= 65535");
	}
	
	this.ipAddress = ipAddress;
	this.port = port;

	stateSink = Sinks
		.many()
		.multicast()
		.<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	stateFlux = stateSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(sub -> {
//		    System.out.println("A new subscriber to status flux: " + sub);
		})
		.log();

	linkSink = Sinks
		.many()
		.multicast()
		.<Link>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	linkFlux = linkSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(sub -> {
//		    System.out.println("A new subscriber to link flux: " + sub);
		})
		.log();

	messageSink = Sinks
		.many()
		.multicast()
		.<Message>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	messageFlux = messageSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic()).doOnSubscribe(sub -> {
//		    System.out.println("A new subscriber to message flux: " + sub);
		})
		.log();

	// FIXME It'd be better to take state machine related implementation out of this
	// class
	try {
	    fluxerMachineBuilder = StateMachineBuilder.builder();
	    fluxerMachineBuilder.configureConfiguration()
	    	.withConfiguration()
	    	.autoStartup(false);

	    fluxerMachineBuilder.configureStates()
	    	.withStates()
	    	.initial(State.STOPPED, publishStateChange())
	    	.state(State.STOPPED)
	    	.state(State.STARTED)
	    	.state(State.STARTING, processStartRequest())
	    	.state(State.STOPPING, processStopRequest());

	    fluxerMachineBuilder.configureTransitions()
	    	.withExternal()
	    	.source(State.STOPPED)
	    	.event(Event.START_REQUESTED)
	    	.target(State.STARTING)
	    	.action(publishStateChange())
	    	.and()
	    	.withExternal()
	    	.source(State.STARTING)
	    	.event(Event.PROCESSED)
	    	.target(State.STARTED)
	    	.action(publishStateChange())
	    	.and()
	    	.withExternal()
	    	.source(State.STARTING)
	    	.event(Event.FAILED)
	    	.target(State.STOPPED)
	    	.action(publishStateChange())
	    	.and()
	    	.withExternal()
	    	.source(State.STARTED)
	    	.event(Event.STOP_REQUESTED)
	    	.target(State.STOPPING)
	    	.action(publishStateChange())
	    	.and()
	    	.withExternal()
	    	.source(State.STOPPING)
	    	.event(Event.PROCESSED)
	    	.target(State.STOPPED)
	    	.action(publishStateChange())
	    	.and()
	    	// Currently this transition is not used
	    	.withExternal()
	    	.source(State.STOPPING)
	    	.event(Event.FAILED)
	    	.target(State.STOPPED)
	    	.action(publishStateChange());
	} catch (Exception e) {
	    throw new FluxerException(e.getLocalizedMessage());
	}

	fluxerMachine = fluxerMachineBuilder.build();
	fluxerMachine.addStateListener(new StateMachineListener<Fluxer.State, Fluxer.Event>() {

	    @Override
	    public void transitionStarted(Transition<State, Event> transition) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void transitionEnded(Transition<State, Event> transition) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void transition(Transition<State, Event> transition) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void stateMachineStopped(StateMachine<State, Event> stateMachine) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void stateMachineStarted(StateMachine<State, Event> stateMachine) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void stateMachineError(StateMachine<State, Event> stateMachine, Exception exception) {
		// TODO Auto-generated method stub
		System.out.println("stateMachineError: " + exception);
	    }

	    @Override
	    public void stateExited(org.springframework.statemachine.state.State<State, Event> state) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void stateEntered(org.springframework.statemachine.state.State<State, Event> state) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void stateContext(StateContext<State, Event> stateContext) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void stateChanged(org.springframework.statemachine.state.State<State, Event> from,
		    org.springframework.statemachine.state.State<State, Event> to) {
		System.out.println("stateChanged: " + (from != null ? from.getIds() : "") + " --> " + to.getIds());

	    }

	    @Override
	    public void extendedStateChanged(Object key, Object value) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void eventNotAccepted(org.springframework.messaging.Message<Event> event) {
		System.out.println("eventNotAccepted: " + event);
	    }
	});
//	System.out.println("state machine starting");

	fluxerMachine.startReactively().doOnError(err -> {
//	    System.out.println("state machine start failed");
	}).doOnSuccess(__ -> {
//	    System.out.println("state machine start is done");
	}).subscribe();
    }

    @Override
    public Mono<Result> start() {

	Sinks.One<Result> resultSink = Sinks.one();

//	org.springframework.statemachine.state.State<State, Event>  currentState = fluxerMachine.getState();
//	System.out.println(currentState);
//	if (fluxerMachine.getState().getId().equals(State.STARTED)) {
//	    resultSink.tryEmitValue(Result.PROCESSED);
//	}
//	else if (fluxerMachine.getState().getId().equals(State.STARTING)) {
//	    
//	}
//	else {
//	    
//	}

	fluxerMachine.getExtendedState().getVariables().put(Event.START_REQUESTED, resultSink);
	fluxerMachine.sendEventCollect(Mono.just(MessageBuilder.withPayload(Event.START_REQUESTED).build()))
		.subscribe(results -> {
		    System.out.println("Sending START_REQUESTED --> " + results);
		    if (!isSentEventAccepted(results)) {
			resultSink.tryEmitValue(new Result(Result.Type.FAILED, "The request can't be accepted as it's currently " + fluxerMachine.getState().getId()));
			removeResultSink(Event.START_REQUESTED);
		    }
		}, error -> {
//		    System.out.println(System.currentTimeMillis() + ": doOnError - 1");
		    resultSink.tryEmitValue(new Result(Result.Type.FAILED, error.getLocalizedMessage()));
		    removeResultSink(Event.START_REQUESTED);
//		    System.out.println(System.currentTimeMillis() + ": doOnError - 2");
		});

	return resultSink.asMono();
    }

    @Override
    public Mono<Result> stop() {

	Sinks.One<Result> resultSink = Sinks.one();

	fluxerMachine.getExtendedState().getVariables().put(Event.STOP_REQUESTED, resultSink);

	fluxerMachine.sendEventCollect(Mono.just(MessageBuilder.withPayload(Event.STOP_REQUESTED).build()))
		.subscribe(results -> {
		    System.out.println("Sending STOP_REQUESTED --> " + results);
		    if (!isSentEventAccepted(results)) {
			resultSink.tryEmitValue(new Result(Result.Type.FAILED, "The request can't be accepted as it's currently " + fluxerMachine.getState().getId()));
			removeResultSink(Event.STOP_REQUESTED);
		    }
		}, error -> {
//		    System.out.println(System.currentTimeMillis() + ": doOnError - 1");
		    resultSink.tryEmitValue(new Result(Result.Type.FAILED, error.getLocalizedMessage()));
		    removeResultSink(Event.STOP_REQUESTED);
//		    System.out.println(System.currentTimeMillis() + ": doOnError - 2");
		});

	return resultSink.asMono();
    }

    @Override
    public Mono<Result> send(byte[] message, Endpoint remote) {
	// If writing to multiple links needs to be allowed,
	// - Change isSameLink() to support wildcard or regex or something else
	// - Return a new future created from multiple ChannelFuture
	// - See
	// https://stackoverflow.com/questions/10503586/writing-to-all-but-one-in-a-tcp-netty-channelgroup

	Sinks.One<Result> resultSink = Sinks.one();

	if (group != null) {
	    Iterator<Channel> channelIterator = group.iterator();
	    boolean linkFound = false;
	    Channel channel = null;
	    while (channelIterator.hasNext()) {
		channel = channelIterator.next();
		if (isSameLink(channel, remote)) {
		    linkFound = true;
		    break;
		}
	    }

	    if (linkFound) {
		InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
		final Endpoint local = new Endpoint(localAddress.getAddress().getHostAddress(), localAddress.getPort());
		
		channel.writeAndFlush(Unpooled.wrappedBuffer(message))
			.addListener(new ChannelFutureListener() {
			    @Override
			    public void operationComplete(ChannelFuture future) throws Exception {
//				System.out.println("writing to " + remote + " completed: " + ByteBufUtil.hexDump(message));
				resultSink.tryEmitValue(new Result(Result.Type.PROCESSED, "Successfully sent to " + remote + ":" + ByteBufUtil.hexDump(message)));
				emitMessage(local, remote, message);
			    }
			});
	    } else {
		resultSink.tryEmitValue(new Result(Result.Type.FAILED, "Matching link is not found: " + remote));
	    }
	}

	return resultSink.asMono();
    }

    @Override
    public Flux<State> state() {
	return stateFlux;
    }

    @Override
    public Flux<Link> link() {
	return linkFlux;
    }

    @Override
    public Flux<Message> message() {
	return messageFlux;
    }

    @Override
    public void close() throws Exception {
	System.out.println("close() started");

	closeConnections();

	if (stateSink != null) {
	    stateSink.tryEmitComplete();
	}
	if (linkSink != null) {
	    linkSink.tryEmitComplete();
	}
	if (messageSink != null) {
	    messageSink.tryEmitComplete();
	}

	final CountDownLatch countDownLatch5 = new CountDownLatch(1);
	if (fluxerMachine != null) {
	    fluxerMachine.stopReactively().doOnError(err -> {
//		System.out.println("state machine stop failed");
		countDownLatch5.countDown();
	    }).doOnSuccess(__ -> {
		System.out.println("state machine stop is done");
		countDownLatch5.countDown();
	    }).subscribe();
	} else {
//	    System.out.println("state machine is null");
	    countDownLatch5.countDown();
	}
	try {
	    countDownLatch5.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
//	    System.out.println("error");
	}
	fluxerMachine = null;

	System.out.println("close() done");
    }

    public String getIpAddress() {
	return ipAddress;
    }

    public Integer getPort() {
	return port;
    }

    @SuppressWarnings("unchecked")
    protected Sinks.One<Result> getResultSink(Event event) {
	return (One<Result>) (fluxerMachine.getExtendedState().getVariables().get(event));
    }

    protected void removeResultSink(Event event) {
	fluxerMachine.getExtendedState().getVariables().remove(event);
    }

    protected void sendEventToStateMachine(Event eventToSend) {
	fluxerMachine.sendEvent(
		Mono.just(MessageBuilder.withPayload(eventToSend).build()))
		.subscribe(result -> {
		});
    }
    
    private void emitState(State state)
    {
	stateSink.tryEmitNext(state);
    }

    protected void emitLink(Connection connection, Link.State state) {
	Link link = getLinkFromConnection((InetSocketAddress) connection.channel().localAddress(),
		(InetSocketAddress) connection.channel().remoteAddress(), state);
//	System.out.println("link updated: " + newLink + " --> " + state.toString());
	linkSink.tryEmitNext(link);
    }

//
    protected void emitMessage(Connection connection, byte[] receivedMessage) {
	Link link = getLinkFromConnection((InetSocketAddress) connection.channel().localAddress(),
		(InetSocketAddress) connection.channel().remoteAddress(), Link.State.NONE);
//	System.out.println("inbound updated: " + link + " --> " + ByteBufUtil.hexDump(receivedMessage));
	messageSink.tryEmitNext(new Message(Type.INBOUND, link, receivedMessage));
    }
    
    protected void emitMessage(Endpoint local, Endpoint remote, byte[] receivedMessage) {
	Link link = new Link(Link.State.NONE, local, remote);
//	System.out.println("inbound updated: " + link + " --> " + ByteBufUtil.hexDump(receivedMessage));
	messageSink.tryEmitNext(new Message(Type.OUTBOUND, link, receivedMessage));
    }
    
    private Link getLinkFromConnection(InetSocketAddress local, InetSocketAddress remote, Link.State state) {
	return new Link(state, new Endpoint(local.getAddress().getHostAddress(), local.getPort()),
		new Endpoint(remote.getAddress().getHostAddress(), remote.getPort()));
    }

    private Action<State, Event> publishStateChange() {
	return new Action<Fluxer.State, Fluxer.Event>() {
	    @Override
	    public void execute(StateContext<State, Event> context) {
		emitState(context.getTransition().getTarget().getId());
	    }
	};
    }

    private Action<State, Event> processStartRequest() {
	return new Action<State, Event>() {

	    @Override
	    public void execute(StateContext<State, Event> context) {
		System.out.println("processStartRequest-start");

		handler = createHandler();
		executor = new DefaultEventExecutor();
		group = new DefaultChannelGroup(executor);
		createTcpConnection();
	    }
	};
    }

    private Action<State, Event> processStopRequest() {
	return new Action<State, Event>() {

	    @Override
	    public void execute(StateContext<State, Event> context) {
		System.out.println("processStopRequest-start");

		closeConnections();
		System.out.println("processStopRequest-done, will send PROCESSED");

		// What do I do if stopping doesn't work well?
		sendEventToStateMachine(Event.PROCESSED);

		@SuppressWarnings("unchecked")
		Sinks.One<Result> resultSink = (One<Result>) (fluxerMachine.getExtendedState().getVariables().get(Event.STOP_REQUESTED));
		if (resultSink != null) {
		    resultSink.tryEmitValue(new Result(Result.Type.PROCESSED, "Successfully stopped"));
		    fluxerMachine.getExtendedState().getVariables().remove(Event.STOP_REQUESTED);
		}
	    }
	};
    }

    private boolean isSentEventAccepted(List<StateMachineEventResult<State, Event>> results) {
	return results.stream()
		.anyMatch(result -> result.getResultType().equals(ResultType.ACCEPTED));
    }

    private BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> createHandler() {
	return new BiFunction<NettyInbound, NettyOutbound, Publisher<Void>>() {
	    @Override
	    public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
		in.withConnection(tcpConnection -> {
//		    System.out.println("withConnection");
//		    System.out.println(tcpConnection.channel().localAddress().toString());
//		    System.out.println(tcpConnection.channel().remoteAddress().toString());
		}).receive().asByteArray().doOnCancel(() -> {
//		    System.out.println("in doOnCancel");
		}).doOnComplete(() -> {
//		    System.out.println("in doOnComplete");
		}).doOnNext(buf -> {
		    in.withConnection(connection -> {
//						System.out.println(ByteBufUtil.hexDump(buf) + " from " + connection + "???");
//			System.out.println(ByteBufUtil.hexDump(buf) + " from " + connection + "???");
//			System.out.println(connection.channel().localAddress().toString());
//			System.out.println(connection.channel().remoteAddress().toString());
			emitMessage(connection, buf);
		    });
		}).doOnError(e -> {
//		    System.out.println("in doOnError " + e);
		}).doOnSubscribe(s -> {
//		    System.out.println("in doOnSubscribe " + s);
		}).doOnTerminate(() -> {
//		    System.out.println("in doOnTerminate");
		}).subscribe();

		return out.neverComplete().doOnTerminate(() -> {
//		    System.out.println("out doOnTerminate");
		}).doOnError(ex -> {
//		    System.out.println("out doOnError: " + ex.getMessage());
		}).doOnCancel(() -> {
//		    System.out.println("out doOnCancel");
		});
	    }
	};
    }

    private boolean isSameLink(@NonNull Channel channel, @NonNull Endpoint remote) {
	InetSocketAddress channelRemoteAddress = (InetSocketAddress) channel.remoteAddress();
	if (remote.getIpAddress().equals(channelRemoteAddress.getAddress().getHostAddress())
		&& remote.getPort().equals(Integer.valueOf(channelRemoteAddress.getPort()))) {
	    return true;
	}

	return false;
    }

    private void closeConnections() {
	long now = System.currentTimeMillis();
	final CountDownLatch countDownLatch1 = new CountDownLatch(1);
	if (disposableChannel != null) {
//	    System.out.println("disposableChannel closinge");
	    disposableChannel.dispose();
	    disposableChannel
		    .onDispose()
		    .doOnError(err -> {
//			System.out.println("disposableChannel close failed");
			countDownLatch1.countDown();
		    })
		    .doOnSuccess(__ -> {
			System.out.println("1. disposableChannel close done");
			countDownLatch1.countDown();
		    })
		    .subscribe();
	} else {
//	    System.out.println("disposableChannel is null");
	    countDownLatch1.countDown();
	}
	try {
	    countDownLatch1.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
//	    System.out.println("error");
	}

	final CountDownLatch countDownLatch2 = new CountDownLatch(1);
	if (group != null) {
	    group.disconnect().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    System.out.println("2. group disconnect done");
		    countDownLatch2.countDown();
		}
	    });
	} else {
//	    System.out.println("group is null 1");
	    countDownLatch2.countDown();
	}
	try {
	    countDownLatch2.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
//	    System.out.println("error");
	}

	final CountDownLatch countDownLatch3 = new CountDownLatch(1);
	if (group != null) {
	    group.close().addListener(new ChannelGroupFutureListener() {
		@Override
		public void operationComplete(ChannelGroupFuture future) throws Exception {
		    System.out.println("3. group close done");
		    countDownLatch3.countDown();
		}
	    });
	} else {
//	    System.out.println("group is null 2");
	    countDownLatch3.countDown();
	}
	try {
	    countDownLatch3.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
//	    System.out.println("error");
	}

	final CountDownLatch countDownLatch4 = new CountDownLatch(1);
	if (executor != null) {
	    executor.shutdownGracefully().addListener(new GenericFutureListener<Future<Object>>() {
		@Override
		public void operationComplete(Future<Object> future) throws Exception {
		    System.out.println("4 executor close done");
		    countDownLatch4.countDown();
		}
	    });
	} else {
//	    System.out.println("executor is null");
	    countDownLatch4.countDown();
	}
	try {
	    countDownLatch4.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
//	    System.out.println("error");
	}
	disposableChannel = null;
	group = null;
	executor = null;
	System.out.println("closeConnections done: " + (System.currentTimeMillis() - now));
    }
}
