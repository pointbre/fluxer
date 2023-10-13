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
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.transition.Transition;

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

    private StateMachine<State, Event> fluxerMachine;

    public AbstractFluxer() throws Exception {
	stateSink = Sinks
		.many()
		.multicast()
		.<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	stateFlux = stateSink
		.asFlux()
		.publishOn(Schedulers.boundedElastic())
		.doOnSubscribe(subscriber -> {
		    log.trace("A new subscriber to status flux: " + subscriber);
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
		    log.trace("A new subscriber to link flux: " + subscriber);
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
		    log.trace("A new subscriber to message flux: " + subscriber);
		})
		.log();

	fluxerMachine = getFluxerStateMachineBuilder().build();
	fluxerMachine.addStateListener(new FluxerMachineStateListener());
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

    abstract protected Action<State, Event> processStartRequest();

    abstract protected Action<State, Event> processStopRequest();

    abstract protected void closeLinks();

    protected void putResultSink(Event event, Sinks.One<Result> resultSink) {
	fluxerMachine.getExtendedState().getVariables().put(event, resultSink);
    }

    @SuppressWarnings("unchecked")
    protected Sinks.One<Result> getResultSink(Event event) {
	return (One<Result>) (fluxerMachine.getExtendedState().getVariables().get(event));
    }

    protected void removeResultSink(Event event) {
	fluxerMachine.getExtendedState().getVariables().remove(event);
    }

    protected Mono<List<StateMachineEventResult<State, Event>>> sendEvent(Event eventToSend) {
	return fluxerMachine.sendEventCollect(Mono.just(MessageBuilder.withPayload(eventToSend).build()));
    }

    protected boolean isEventAccepted(List<StateMachineEventResult<State, Event>> results) {
	return results.stream().anyMatch(result -> result.getResultType().equals(ResultType.ACCEPTED));
    }

    protected State getState() {
	return fluxerMachine.getState().getId();
    }
    
    protected Many<Link> getLinkSink() {
	return linkSink;
    }
    
    protected Many<Message> getMessageSink() {
	return messageSink;
    }
    
    private Builder<State, Event> getFluxerStateMachineBuilder() throws Exception {
	Builder<State, Event> builder = StateMachineBuilder.builder();

	builder.configureConfiguration()
		.withConfiguration()
		.autoStartup(false);

	builder.configureStates()
		.withStates()
		.initial(State.STOPPED, publishStateChange())
		.state(State.STOPPED)
		.state(State.STARTED)
		.state(State.STARTING, processStartRequest())
		.state(State.STOPPING, processStopRequest());

	builder.configureTransitions()
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

	return builder;
    }

    private final class FluxerMachineStateListener implements StateMachineListener<Fluxer.State, Fluxer.Event> {
	@Override
	public void transitionStarted(Transition<State, Event> transition) {
	}

	@Override
	public void transitionEnded(Transition<State, Event> transition) {
	}

	@Override
	public void transition(Transition<State, Event> transition) {
	}

	@Override
	public void stateMachineStopped(StateMachine<State, Event> stateMachine) {
	}

	@Override
	public void stateMachineStarted(StateMachine<State, Event> stateMachine) {
	}

	@Override
	public void stateMachineError(StateMachine<State, Event> stateMachine, Exception exception) {
	    System.out.println("stateMachineError: " + exception);
	}

	@Override
	public void stateExited(org.springframework.statemachine.state.State<State, Event> state) {
	}

	@Override
	public void stateEntered(org.springframework.statemachine.state.State<State, Event> state) {
	}

	@Override
	public void stateContext(StateContext<State, Event> stateContext) {
	}

	@Override
	public void stateChanged(org.springframework.statemachine.state.State<State, Event> from,
		org.springframework.statemachine.state.State<State, Event> to) {
	    System.out.println("stateChanged: " + (from != null ? from.getIds() : "") + " --> " + to.getIds());
	}

	@Override
	public void extendedStateChanged(Object key, Object value) {
	}

	@Override
	public void eventNotAccepted(org.springframework.messaging.Message<Event> event) {
	    System.out.println("eventNotAccepted: " + event);
	}
    }

    private Action<State, Event> publishStateChange() {
	return new Action<Fluxer.State, Fluxer.Event>() {
	    @Override
	    public void execute(StateContext<State, Event> context) {
		emitState(context.getTransition().getTarget().getId());
	    }
	};
    }
    
    private void emitState(State state) {
	stateSink.tryEmitNext(state);
    }

}