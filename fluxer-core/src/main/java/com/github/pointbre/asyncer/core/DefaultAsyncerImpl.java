package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;
import reactor.util.annotation.Nullable;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
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

    private record Request(UUID uuid, Event event, One<TransitionResult> resultSink) {
    }

    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final Thread transitionHandler;
    private final static Request POISON_PILL = new Request(null, null, null);
    private final AtomicBoolean isBeingClosed = new AtomicBoolean(false);

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
		Request request = null;

		try {
		    request = requests.take();
		} catch (InterruptedException e) {
		    System.out.println("While waiting for a new request, interrupted");
		    break;
		}

		System.out.println("Request received " + request);

		if (request.equals(POISON_PILL)) {
		    System.out.println("Poison pill arrived");
		    break;
		}

		final UUID uuid = request.uuid();
		final Event event = request.event();
		One<TransitionResult> resultSink = request.resultSink();

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

		// If being closed now, just return the result now
		if (isBeingClosed.get()) {
		    resultSink.tryEmitValue(new TransitionResult(uuid, event, null, null, null, null, Boolean.FALSE,
			    "Being closed now, so not possible to process the event: " + event));
		} else {
		    System.out.println("Running");
		    final Transition transition = matchingTransition.get();
		    final TransitionResult transitionResult = transitionExecutor.run(uuid, event, transition);
		    System.out.println("Returned transition result=" + transitionResult);
		    if (transitionResult.getToState() != null && !currentState.equals(transitionResult.getToState())) {
			currentState = transitionResult.getToState();
			try {
			    stateSink.tryEmitNext(currentState);
			} catch (Exception e) {
			    // TODO Auto-generated catch block
			    e.printStackTrace();
			}
		    } else {
			System.out.println("state hasn't changed");
		    }

		    if (transitionResult.getTaskResults() != null) {
			transitionResult.getTaskResults().forEach(r -> System.out.println("task result=" + r));
		    }

		    try {
			resultSink.tryEmitValue(transitionResult);
		    } catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		    }
		    System.out.println("Processing done: " + transitionResult);
		}
	    }
	});

	this.currentState = initialState;
	stateSink.tryEmitNext(initialState);

    }

    @Override
    public Mono<TransitionResult> fire(@NonNull UUID uuid, @NonNull Event event) {

	final One<TransitionResult> resultSink = Sinks.one();

	try {
	    requests.put(new Request(uuid, event, resultSink));
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
	// Add
//	transitionHandler.interrupt();
	try {
	    System.out.println("Putting poison pill");
	    requests.put(POISON_PILL);
	    isBeingClosed.set(true);
	    System.out.println("Waiting until the poison pill is taken");
	    System.out.println("");
	    while (!requests.isEmpty()) {
		System.out.print(".");
		Thread.sleep(Duration.ofMillis(100));
	    }
	    System.out.println("Poison pill is taken");
	} catch (InterruptedException e) {
	}
    }
}
