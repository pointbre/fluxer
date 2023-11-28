package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
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

public class DefaultAsyncerImpl<S, E, R> implements Asyncer<S, E, R> {

	private final UUID uuid = AsyncerUtil.generateType1UUID();

	@NonNull
	private final S initialState;

	@Nullable
	private final S finalState;

	@NonNull
	private final Set<Transition<S, E, R>> transitions;

	@NonNull
	private final TransitionExecutor<S, E, R> transitionExecutor;

	private record Request<S, E, R>(UUID uuid, E event, One<TransitionResult<S, E, R>> resultSink) {
	}

	private final BlockingQueue<Request<S, E, R>> requests = new LinkedBlockingQueue<>();
	private final Thread transitionHandler;
	private final Request<S, E, R> POISON_PILL = new Request<>(null, null, null);
	private final AtomicBoolean isBeingClosed = new AtomicBoolean(false);

	private final Many<StateChange<S>> stateSink = Sinks.many().multicast()
			.<StateChange<S>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	private S currentState;

	private final Many<TransitionResult<S, E, R>> transitionResultSink = Sinks.many().multicast()
			.<TransitionResult<S, E, R>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE,
					false);

	public DefaultAsyncerImpl(@NonNull S initialState, @Nullable S finalState,
			@NonNull Set<Transition<S, E, R>> transitions, @NonNull TransitionExecutor<S, E, R> transitionExecutor) {

		this.initialState = initialState;
		this.finalState = finalState;
		this.transitions = transitions;
		this.transitionExecutor = transitionExecutor;

		// FIXME Better put this into a separate class? Or injected?
		this.transitionHandler = Thread.ofVirtual().name("asyncer-transition-handler").start(() -> {

			while (true) {
				Request<S, E, R> request = null;

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
				final E event = request.event();
				One<TransitionResult<S, E, R>> resultSink = request.resultSink();

				if (finalState != null && currentState.equals(finalState)) {
					resultSink.tryEmitValue(new TransitionResult<>(uuid, event, null, null, null, Boolean.FALSE,
							event + " is a final state"));
					continue;
				}

				final Optional<Transition<S, E, R>> matchingTransition = transitions.stream()
						.filter(t -> t.getFrom().equals(currentState) && t.getEvent().equals(event)).findFirst();
				if (!matchingTransition.isPresent()) {
					resultSink.tryEmitValue(new TransitionResult<>(uuid, event, null, null, null, Boolean.FALSE,
							"No matching transition found from " + currentState + " triggered by " + event));
					continue;
				}

				// If being closed now, just return the result now
				if (isBeingClosed.get()) {
					resultSink.tryEmitValue(new TransitionResult<>(uuid, event, null, null, null, Boolean.FALSE,
							"Being closed now, so not possible to process the event: " + event));
					continue;
				}

				System.out.println("Running");
				final Transition<S, E, R> transition = matchingTransition.get();
				final TransitionResult<S, E, R> transitionResult = transitionExecutor.run(uuid, currentState, event,
						transition, stateSink);

				System.out.println("Returned transition result=" + transitionResult);
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
		});

		this.currentState = initialState;
		stateSink.tryEmitNext(new StateChange<>(AsyncerUtil.generateType1UUID(), initialState));

	}

	@Override
	public Mono<TransitionResult<S, E, R>> fire(@NonNull UUID uuid, @NonNull E event) {

		final One<TransitionResult<S, E, R>> resultSink = Sinks.one();

		try {
			requests.put(new Request<>(uuid, event, resultSink));
		} catch (InterruptedException e) {
			TransitionResult<S, E, R> transitionResult = new TransitionResult<>(uuid, event, null, null, null,
					Boolean.FALSE, "Failed to fire the event: " + event);
			resultSink.tryEmitValue(transitionResult);
			transitionResultSink.tryEmitNext(transitionResult);
		}

		return resultSink.asMono();

	}

	@Override
	public Flux<StateChange<S>> stateChange() {
		return stateSink.asFlux();
	}

	@Override
	public Flux<TransitionResult<S, E, R>> transitionResult() {
		return transitionResultSink.asFlux();
	}

	@Override
	public UUID uuid() {
		return uuid;
	}

	@Override
	public void close() throws Exception {
		// FIXME If close() is called again, this will do infinite loop

		System.out.println("Asyncer's close() called");

		stateSink.tryEmitComplete();
		transitionResultSink.tryEmitComplete();

		// Add
		// transitionHandler.interrupt();
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
