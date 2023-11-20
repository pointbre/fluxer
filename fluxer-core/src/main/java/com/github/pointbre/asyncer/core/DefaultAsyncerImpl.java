package com.github.pointbre.asyncer.core;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.annotation.Nullable;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public class DefaultAsyncerImpl implements Asyncer {

    @NonNull
    private final HashSet<State> states;

    @NonNull
    private final State initialState;

    @Nullable
    private final State finalState;

    @NonNull
    private final HashSet<Event> events;

    @NonNull
    private final HashSet<Transition> transitions;

    @NonNull
    private final TransitionExecutor transitionExecutor;

    private final BlockingQueue<Tuple3<UUID, Event, One<TransitionResult>>> requests = new LinkedBlockingQueue<>();
    private final Thread transitionHandler;

    private final Many<State> stateSink = Sinks.many().multicast().<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE,
	    false);
    private State currentState;

    public DefaultAsyncerImpl(@NonNull HashSet<State> states, @NonNull State initialState, @Nullable State finalState,
	    @NonNull HashSet<Event> events, @NonNull HashSet<Transition> transitions,
	    @NonNull TransitionExecutor transitionExecutor) {
	this.states = states;
	this.initialState = initialState;
	this.finalState = finalState;
	this.events = events;
	this.transitions = transitions;
	this.transitionExecutor = transitionExecutor;

	// FIXME Better put this into a separate class? Or injected?
	this.transitionHandler = Thread.ofVirtual().name("asyncer-transition-handler").start(() -> {

	    while (true) {
		Tuple3<UUID, Event, One<TransitionResult>> request = null;

		try {
		    request = requests.take();
		} catch (InterruptedException e) {
		    System.out.println("While waiting for a new request, interrupted");
		    break;
		}

		final UUID uuid = request.getT1(); // FIXME what to do with this?
		final Event event = request.getT2();
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

		final Optional<Transition> matchingTransition = transitions.stream()
			.filter(t -> t.getFrom().equals(currentState) && t.getEvent().equals(event)).findFirst();
		if (!matchingTransition.isPresent()) {
		    resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
			    "No matching transition found from " + currentState + " triggered by " + event));
		    continue;
		}

		final Transition transition = matchingTransition.get();
		final TransitionResult transitionResult = transitionExecutor.run(uuid, event, transition);
		if (transitionResult.getToState() != null && !currentState.equals(transitionResult.getToState())) {
		    currentState = transitionResult.getToState();
		    stateSink.tryEmitNext(currentState);
		}

		if (transitionResult.getTaskResults() != null) {
		    transitionResult.getTaskResults().forEach(r -> System.out.println("task result=" + r));
		}

		resultSink.tryEmitValue(transitionResult);
		System.out.println("Processing done: " + transitionResult);

	    }
	});

	this.currentState = initialState;
	stateSink.tryEmitNext(initialState);

    }

    @Override
    public Mono<TransitionResult> fire(@NonNull UUID uuid, @NonNull Event event) {

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
