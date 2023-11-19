package com.github.pointbre.asyncer.core;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public class AsyncerImpl implements Asyncer {

    private final HashSet<State> states;
    private final State initialState;
    private final State finalState;
    private final HashSet<Event> events;
    private final HashSet<Transition> transitions;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final BlockingQueue<Tuple3<UUID, Event, One<TransitionResult>>> requests = new LinkedBlockingQueue<>();
    private final Thread transitionHandler;

    private final Many<State> stateSink = Sinks.many().multicast().<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE,
	    false);
    private State currentState;

    public AsyncerImpl(HashSet<State> states, State initialState, State finalState, HashSet<Event> events,
	    HashSet<Transition> transitions) {
	this.states = states;
	this.initialState = initialState;
	this.finalState = finalState;
	this.events = events;
	this.transitions = transitions;

	this.transitionHandler = Thread.ofVirtual().name("asyncer-transition-handler").start(() -> {

	    while (true) {
		Tuple3<UUID, Event, One<TransitionResult>> request = null;

		try {
		    request = requests.take();
		} catch (InterruptedException e) {
		    System.out.println("While waiting for a new request, interrupted");
		    break;
		}

		UUID uuid = request.getT1(); // FIXME what to do with this?
		Event event = request.getT2();
		One<TransitionResult> resultSink = request.getT3();

		// FIXME if event is changed to enum, I don't need to worry about this???
		if (!events.contains(event)) {
		    resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
			    event + " is not included in the allowed event list"));
		    continue;
		}
		if (currentState.equals(finalState)) {
		    resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
			    event + " is a final state"));
		    continue;
		}

		Optional<Transition> matchingTransition = transitions.stream()
			.filter(t -> t.getFrom().equals(currentState) && t.getEvent().equals(event))
			.findFirst();
		if (!matchingTransition.isPresent()) {
		    resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
			    "No matching transition found from " + currentState + " triggered by " + event));
		    continue;
		}
		
		Transition transition = matchingTransition.get();
		Action action = null;
		TransitionResult transitionResult = null;
		State updatedState = null;
		if (transition instanceof StaticTransition t) {
		    action = t.getAction();
		    if (action != null) {
			try (var executor = Executor.of(t.getAction().getExecutor())) {
			    transitionResult = executor.run(uuid, event, transition);
			} catch (Exception e) {
			    // Exception might be thrown when automatic close() is called 
			}
		    }
		    updatedState = t.getTo();
		} else if (transition instanceof DynamicTransition t) {
		    action = t.getAction();
		    if (action == null) {
			resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
				"Action of dynamic transition shouldn't be null: " + transition));
			continue;
		    }
		    try (var executor = Executor.of(t.getAction().getExecutor())) {
			transitionResult = executor.run(uuid,event, transition);
			updatedState = transitionResult.getToState();
		    } catch (Exception e) {
			// Exception might be thrown when automatic close() is called
		    }
		}

		if (transitionResult.getTaskResults() != null) {
		    transitionResult.getTaskResults().forEach(r -> System.out.println("executor result=" + r));
		}

		// Update current state only if it has changed
		if (updatedState != null && !currentState.equals(updatedState)) {
		    currentState = updatedState;
		    stateSink.tryEmitNext(updatedState);
		}

		resultSink.tryEmitValue(transitionResult);
		System.out.println("Processing done: " + transitionResult);
	    }
	});
	this.currentState = initialState;

	stateSink.tryEmitNext(initialState);

    }

    @Override
    public Mono<TransitionResult> fire(UUID uuid, Event event) {

	final One<TransitionResult> resultSink = Sinks.one();

	try {
	    requests.put(Tuples.of(uuid, event, resultSink));
	} catch (InterruptedException e) {
	    resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
		    "Failed to fire the event: " + event));
	}

	return resultSink.asMono();

    }

    @Override
    public Flux<State> state() {

	return stateSink.asFlux();

    }

    @Override
    public void close() throws Exception {

	System.out.println("Asyncer's close() called");
	transitionHandler.interrupt();

    }
}
