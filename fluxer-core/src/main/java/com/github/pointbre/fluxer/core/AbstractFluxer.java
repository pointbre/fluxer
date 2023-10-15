/**
 * 
 */
package com.github.pointbre.fluxer.core;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.StateMachineEventResult.ResultType;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.config.StateMachineBuilder.Builder;

import com.github.pointbre.fluxer.core.Fluxer.EndPoint;
import com.github.pointbre.fluxer.core.Fluxer.Link;
import com.github.pointbre.fluxer.core.Fluxer.Message;
import com.github.pointbre.fluxer.core.Fluxer.Message.Type;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

/**
 * 
 */
@Slf4j
public abstract class AbstractFluxer implements Fluxer {
    private Many<State> stateSink;
    private Flux<State> stateFlux;

    private Many<Link> linkSink;
    private Flux<Link> linkFlux;

    private Many<Message> messageSink;
    private Flux<Message> messageFlux;
    
    private Many<Log> logSink;
    private Flux<Log> logFlux;

    private StateMachine<Fluxer.State.Type, Fluxer.State.Event> fluxerMachine;

    public AbstractFluxer() throws Exception {
	stateSink = Sinks
		.many()
		.multicast()
		.<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	stateFlux = stateSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    log.debug("A new subscriber to status flux: " + subscriber);
		})
		.log();

	linkSink = Sinks
		.many()
		.multicast()
		.<Link>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	linkFlux = linkSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    log.debug("A new subscriber to link flux: " + subscriber);
		})
		.log();

	messageSink = Sinks
		.many()
		.multicast()
		.<Message>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	messageFlux = messageSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    log.debug("A new subscriber to message flux: " + subscriber);
		})
		.log();
	
	logSink = Sinks
		.many()
		.multicast()
		.<Log>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	logFlux = logSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    log.debug("A new subscriber to log flux: " + subscriber);
		})
		.log();

	fluxerMachine = getFluxerStateMachineBuilder().build();
	fluxerMachine.startReactively()
		.doOnError(err -> {
		})
		.doOnSuccess(__ -> {
		})
		.subscribe();
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
    public Flux<Log> log() {
	return logFlux;
    }

    @Override
    public void close() throws Exception {

	closeLinks();

	if (stateSink != null) {
	    stateSink.tryEmitComplete();
	}
	if (linkSink != null) {
	    linkSink.tryEmitComplete();
	}
	if (messageSink != null) {
	    messageSink.tryEmitComplete();
	}
	if (logSink != null) {
	    logSink.tryEmitComplete();
	}

	final CountDownLatch countDownLatch = new CountDownLatch(1);
	if (fluxerMachine != null) {
	    fluxerMachine.stopReactively().doOnError(err -> {
		countDownLatch.countDown();
	    }).doOnSuccess(__ -> {
		countDownLatch.countDown();
	    }).subscribe();
	} else {
	    countDownLatch.countDown();
	}
	try {
	    countDownLatch.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	}
    }

    abstract protected Action<Fluxer.State.Type, Fluxer.State.Event> processStartRequest();

    abstract protected Action<Fluxer.State.Type, Fluxer.State.Event> processStopRequest();

    abstract protected void closeLinks();

    protected void putResultSink(Fluxer.State.Event event, Sinks.One<Result> resultSink) {
	fluxerMachine.getExtendedState().getVariables().put(event, resultSink);
    }

    @SuppressWarnings("unchecked")
    protected Sinks.One<Result> getResultSink(Fluxer.State.Event event) {
	return (One<Result>) (fluxerMachine.getExtendedState().getVariables().get(event));
    }

    protected void removeResultSink(Fluxer.State.Event event) {
	fluxerMachine.getExtendedState().getVariables().remove(event);
    }

    protected Mono<List<StateMachineEventResult<Fluxer.State.Type, Fluxer.State.Event>>> sendEvent(Fluxer.State.Event eventToSend) {
	return fluxerMachine.sendEventCollect(Mono.just(MessageBuilder.withPayload(eventToSend).build()));
    }

    protected boolean isEventAccepted(List<StateMachineEventResult<Fluxer.State.Type, Fluxer.State.Event>> results) {
	return results.stream().anyMatch(result -> result.getResultType().equals(ResultType.ACCEPTED));
    }

    protected Fluxer.State.Type getFluxerState() {
	return fluxerMachine.getState().getId();
    }
    
    protected String getFluxerId() {
	return fluxerMachine.getUuid().toString();
    }

    protected Many<Link> getLinkSink() {
	return linkSink;
    }

    protected Many<Message> getMessageSink() {
	return messageSink;
    }
    
    protected void emitLink(String id, Link.State state, EndPoint local, EndPoint remote) {
	getLinkSink().tryEmitNext(new Link(id, state, local, remote));
    }

    protected void emitMessage(Message.Type type, EndPoint local, EndPoint remote, byte[] receivedMessage) {
	getMessageSink().tryEmitNext(new Message(type, local, remote, receivedMessage));
    }

    private Builder<Fluxer.State.Type, Fluxer.State.Event> getFluxerStateMachineBuilder() throws Exception {
	Builder<Fluxer.State.Type, Fluxer.State.Event> builder = StateMachineBuilder.builder();

	builder.configureConfiguration()
		.withConfiguration()
		.autoStartup(false);

	builder.configureStates()
		.withStates()
		.initial(Fluxer.State.Type.STOPPED, publishStateChange())
		.state(Fluxer.State.Type.STOPPED)
		.state(Fluxer.State.Type.STARTED)
		.state(Fluxer.State.Type.STARTING, processStartRequest())
		.state(Fluxer.State.Type.STOPPING, processStopRequest());

	builder.configureTransitions()
		.withExternal()
		.source(Fluxer.State.Type.STOPPED)
		.event(Fluxer.State.Event.START_REQUESTED)
		.target(Fluxer.State.Type.STARTING)
		.action(publishStateChange())
		.and()
		.withExternal()
		.source(Fluxer.State.Type.STARTING)
		.event(Fluxer.State.Event.PROCESSED)
		.target(Fluxer.State.Type.STARTED)
		.action(publishStateChange())
		.and()
		.withExternal()
		.source(Fluxer.State.Type.STARTING)
		.event(Fluxer.State.Event.FAILED)
		.target(Fluxer.State.Type.STOPPED)
		.action(publishStateChange())
		.and()
		.withExternal()
		.source(Fluxer.State.Type.STARTED)
		.event(Fluxer.State.Event.STOP_REQUESTED)
		.target(Fluxer.State.Type.STOPPING)
		.action(publishStateChange())
		.and()
		.withExternal()
		.source(Fluxer.State.Type.STOPPING)
		.event(Fluxer.State.Event.PROCESSED)
		.target(Fluxer.State.Type.STOPPED)
		.action(publishStateChange())
		.and()
		// Currently this transition is not used
		.withExternal()
		.source(Fluxer.State.Type.STOPPING)
		.event(Fluxer.State.Event.FAILED)
		.target(Fluxer.State.Type.STOPPED)
		.action(publishStateChange());

	return builder;
    }

    private Action<Fluxer.State.Type, Fluxer.State.Event> publishStateChange() {
	return new Action<Fluxer.State.Type, Fluxer.State.Event>() {
	    @Override
	    public void execute(StateContext<Fluxer.State.Type, Fluxer.State.Event> context) {
		emitState(context.getTransition().getTarget().getId(), context.getEvent());
	    }
	};
    }

    private void emitState(Fluxer.State.Type state, Fluxer.State.Event event) {
	stateSink.tryEmitNext(new Fluxer.State(getFluxerId(), state, event));
    }
}