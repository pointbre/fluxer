/**
 * 
 */
package com.github.pointbre.fluxer.core;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.event.Level;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.StateMachineEventResult.ResultType;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.config.StateMachineBuilder.Builder;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

@Slf4j
public abstract class AbstractFluxer<T> implements Fluxer<T> {
    private static final int TIMEOUT = 5;
    private Many<Fluxer.State> stateSink;
    private Many<Fluxer.Link> linkSink;
    private Many<Fluxer.Message<T>> messageSink;
    private Many<Fluxer.Log> logSink;
    
    private Flux<Fluxer.State> stateFlux;
    private Flux<Fluxer.Link> linkFlux;
    private Flux<Fluxer.Message<T>> messageFlux;
    private Flux<Fluxer.Log> logFlux;

    private StateMachine<Fluxer.State.Type, Fluxer.State.Event> fluxerMachine;

    public AbstractFluxer() throws Exception {
	stateSink = Sinks
		.many()
		.multicast().<Fluxer.State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	linkSink = Sinks
		.many()
		.multicast().<Fluxer.Link>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	messageSink = Sinks
		.many()
		.multicast().<Fluxer.Message<T>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	logSink = Sinks
		.many()
		.multicast().<Fluxer.Log>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

	stateFlux = stateSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    emitLog(Level.INFO, "A new subscriber is registered to status flux: " + subscriber);
		})
		.log();
	linkFlux = linkSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    emitLog(Level.INFO, "A new subscriber is registered to link flux: " + subscriber);
		})
		.log();
	messageFlux = messageSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    emitLog(Level.INFO, "A new subscriber is registered to message flux: " + subscriber);
		})
		.log();
	logFlux = logSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    emitLog(Level.INFO, "A new subscriber is registered to log flux: " + subscriber);
		})
		.log();

	emitLog(Level.INFO, "Starting the internal state machine");
	final CountDownLatch countDownLatch = new CountDownLatch(1);
	fluxerMachine = getFluxerStateMachineBuilder().build();
	fluxerMachine.startReactively()
		.doOnError(err -> {
		    emitLog(Level.ERROR, "Failed to start the internal state machine: " + err.getLocalizedMessage(), err);
		    countDownLatch.countDown();
		})
		.doOnSuccess(__ -> {
		    emitLog(Level.INFO, "The internal state machine has started successfully");
		    countDownLatch.countDown();
		})
		.subscribe();
	try {
	    countDownLatch.await(TIMEOUT, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    emitLog(Level.ERROR, "Starting the internal state machine has timed out");
	}
    }

    @Override
    public Flux<Fluxer.State> state() {
	return stateFlux;
    }

    @Override
    public Flux<Fluxer.Link> link() {
	return linkFlux;
    }

    @Override
    public Flux<Fluxer.Message<T>> message() {
	return messageFlux;
    }

    @Override
    public Flux<Fluxer.Log> log() {
	return logFlux;
    }

    @Override
    public void close() throws Exception {
	closeLinks();

	emitLog(Level.INFO, "Closing the internal state machine");
	final CountDownLatch countDownLatch = new CountDownLatch(1);
	if (fluxerMachine != null) {
	    fluxerMachine.stopReactively().doOnError(err -> {
		emitLog(Level.ERROR, "Failed to stop the internal state machine: " + err.getLocalizedMessage(), err);
		countDownLatch.countDown();
	    }).doOnSuccess(__ -> {
		emitLog(Level.INFO, "The internal state machine has stopped successfully");
		countDownLatch.countDown();
	    }).subscribe();
	} else {
	    countDownLatch.countDown();
	}
	try {
	    countDownLatch.await(TIMEOUT, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    emitLog(Level.ERROR, "Stopping the internal state machine has timed out");
	}
	
	if (stateSink != null) {
	    emitLog(Level.INFO, "Closing state flux");
	    stateSink.tryEmitComplete();
	}
	if (linkSink != null) {
	    emitLog(Level.INFO, "Closing link flux");
	    linkSink.tryEmitComplete();
	}
	if (messageSink != null) {
	    emitLog(Level.INFO, "Closing message flux");
	    messageSink.tryEmitComplete();
	}
	if (logSink != null) {
	    emitLog(Level.INFO, "Closing log flux");
	    logSink.tryEmitComplete();
	}
    }

    abstract protected Action<Fluxer.State.Type, Fluxer.State.Event> processStartRequest();

    abstract protected Action<Fluxer.State.Type, Fluxer.State.Event> processStopRequest();

    abstract protected void closeLinks();

    protected void putResultSink(Fluxer.State.Event event, Sinks.One<Result> resultSink) {
	fluxerMachine.getExtendedState().getVariables().put(event, resultSink);
    }

    @SuppressWarnings("unchecked")
    protected Sinks.One<Fluxer.Result> getResultSink(Fluxer.State.Event event) {
	return (One<Fluxer.Result>) (fluxerMachine.getExtendedState().getVariables().get(event));
    }

    protected void removeResultSink(Fluxer.State.Event event) {
	fluxerMachine.getExtendedState().getVariables().remove(event);
    }

    protected Mono<List<StateMachineEventResult<Fluxer.State.Type, Fluxer.State.Event>>> sendEvent(
	    Fluxer.State.Event eventToSend) {
	return fluxerMachine.sendEventCollect(Mono.just(MessageBuilder.withPayload(eventToSend).build()));
    }

    protected boolean isEventAccepted(List<StateMachineEventResult<Fluxer.State.Type, Fluxer.State.Event>> results) {
	return results.stream().anyMatch(result -> result.getResultType().equals(ResultType.ACCEPTED));
    }

    protected Fluxer.State.Type getFluxerMachineState() {
	return fluxerMachine.getState().getId();
    }

    protected String getFluxerMachineId() {
	return fluxerMachine.getUuid().toString();
    }

    protected Many<Fluxer.Link> getLinkSink() {
	return linkSink;
    }

    protected Many<Fluxer.Message<T>> getMessageSink() {
	return messageSink;
    }

    protected Many<Fluxer.Log> getLogSink() {
	return logSink;
    }

    protected void emitLink(String id, Fluxer.Link.State state, Fluxer.EndPoint local, Fluxer.EndPoint remote) {
	getLinkSink().tryEmitNext(new Fluxer.Link(id, state, local, remote));
    }

    protected void emitMessage(Fluxer.Message.Type type, Fluxer.EndPoint local, Fluxer.EndPoint remote,
	    T receivedMessage) {
	getMessageSink().tryEmitNext(Fluxer.Message.<T>of(type, local, remote, receivedMessage));
    }
    
    protected void emitLog(Level level, String description, Throwable throwable) {
	switch (level) {
	case TRACE:
	    log.trace(description);
	    break;
	case DEBUG:
	    log.debug(description);
	    break;
	case INFO:
	    log.info(description);
	    break;
	case WARN:
	    log.warn(description);
	    break;
	case ERROR:
	    log.error(description);
	    break;
	default:
	    break;
	}
	getLogSink().tryEmitNext(new Fluxer.Log(level, description, throwable));
    }

    protected void emitLog(Level level, String description) {
	emitLog(level, description, null);
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
		emitLog(Level.INFO, "The state of the internal state machine has changed: " + context);
		emitState(context.getTransition().getTarget().getId(), context.getEvent());
	    }
	};
    }

    private void emitState(Fluxer.State.Type state, Fluxer.State.Event event) {
	stateSink.tryEmitNext(new Fluxer.State(getFluxerMachineId(), state, event));
    }
}