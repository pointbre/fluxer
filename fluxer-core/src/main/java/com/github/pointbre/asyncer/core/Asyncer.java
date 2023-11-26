package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import lombok.NonNull;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks.Many;
import reactor.util.annotation.Nullable;

public interface Asyncer<S, E, R> extends AutoCloseable {

	/**
	 * Asyncer's UUID
	 * 
	 * @return
	 */
	UUID uuid();

	/**
	 * Fire the given event identified by the given UUID
	 * 
	 * @param uuid  {@link UUID} of the given event
	 * @param event The event to fire
	 * @return {@link Mono} of {@link TransitionResult}
	 */
	Mono<TransitionResult<S, E, R>> fire(@NonNull UUID uuid, @NonNull E event);

	Flux<StateChange<S>> stateChange();

	Flux<TransitionResult<S, E, R>> transitionResult();

	@Value
	public class StateChange<S> {
		@NonNull
		UUID uuid;

		@NonNull
		S state;
	}

	@Value
	public class Transition<S, E, R> {

		@NonNull
		String name;

		@NonNull
		S from;

		@NonNull
		E event;

		@Nullable
		S to;

		@Nullable
		List<Callable<TaskResult<R>>> tasks;

		@Nullable
		Class<? extends TaskExecutor<R>> taskExecutor;

		@Nullable
		Duration timeout;

		@Nullable
		S toWhenProcessed;

		@Nullable
		S toWhenFailed;
	}

	@Value
	public class TransitionResult<S, E, R> {

		@NonNull
		UUID uuid;

		@NonNull
		E event;

		@Nullable
		List<S> states;

		@Nullable
		Transition<S, E, R> transition;

		@Nullable
		List<TaskResult<R>> taskResults;

		@NonNull
		Boolean result;

		@NonNull
		String description;

	}

	@Value
	public class TaskResult<R> {

		@NonNull
		UUID uuid;

		@NonNull
		R result;

		@NonNull
		String description;

	}

	public sealed interface TransitionExecutor<S, E, R> extends AutoCloseable permits DefaultTransitionExecutorImpl {

		public TransitionResult<S, E, R> run(@NonNull UUID uuid, @NonNull S state, @NonNull E event,
				@NonNull Transition<S, E, R> transition, @NonNull Many<StateChange<S>> stateSink);

		// public static <S, E, R> TransitionExecutor<S, E, R> of(
		// @NonNull Class<? extends TransitionExecutor<S, E, R>> executor) {
		// return new DefaultTransitionExecutorImpl<>();
		// }

	}

	public sealed interface TaskExecutor<R> extends AutoCloseable
			permits ParallelFAETaskExecutor, SequentialFAETaskExecutor {

		public List<TaskResult<R>> run(@NonNull List<Callable<TaskResult<R>>> tasks, @Nullable Duration timeout);

		public static TaskExecutor<Boolean> of(Class<? extends TaskExecutor<Boolean>> taskExecutor) {
			if (taskExecutor.equals(ParallelFAETaskExecutor.class)) {
				return new ParallelFAETaskExecutor();
			}
			return null;
		}

		// public static <R> TaskExecutor<R> of(@NonNull Class<? extends
		// TaskExecutor<R>> taskExecutor) {
		// if (taskExecutor.equals(ParallelFAETaskExecutor.class)) {
		// return new ParallelFAETaskExecutor();
		// } else if (taskExecutor.equals(SequentialFAETaskExecutor.class)) {
		// return new SequentialFAETaskExecutor();
		// }
		// return new ParallelFAETaskExecutor();
		// }

	}

}
