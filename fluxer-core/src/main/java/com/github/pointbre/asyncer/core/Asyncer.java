package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks.Many;
import reactor.util.annotation.Nullable;

public interface Asyncer<S extends State<T>, T, E extends Event<F>, F, R> extends AutoCloseable {

	UUID uuid();

	Flux<Change<S>> state();

	Flux<TransitionResult<S, T, E, F, R>> transition();

	Mono<TransitionResult<S, T, E, F, R>> fire(@NonNull UUID uuid, @NonNull E event);

	@Value
	@NonFinal
	public abstract class Typed<T> {

		@NonNull
		T type;

	}

	@Value
	@NonFinal
	public abstract class Unique {

		@NonNull
		UUID uuid;

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class Change<C> extends Unique {

		@NonNull
		C value;

		public Change(@NonNull UUID uuid, @NonNull C value) {
			super(uuid);
			this.value = value;
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class State<T> extends Typed<T> {

		public State(@NonNull T type) {
			super(type);
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class Event<T> extends Typed<T> {

		protected Event(@NonNull T type) {
			super(type);
		}

	}

	@Value
	public class Transition<S extends State<T>, T, E extends Event<F>, F, R> {

		@NonNull
		String name;

		@NonNull
		S from;

		@NonNull
		E event;

		@Nullable
		S to;

		@Nullable
		List<BiFunction<S, E, Result<R>>> tasks;

		@Nullable
		TaskExecutor<S, T, E, F, R> taskExecutor;

		@Nullable
		Duration timeout;

		@Nullable
		S toWhenProcessed;

		@Nullable
		S toWhenFailed;
	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class Result<R> extends Unique {

		@NonNull
		R value;

		@NonNull
		String description;

		public Result(@NonNull UUID uuid, @NonNull R value, @NonNull String description) {
			super(uuid);
			this.value = value;
			this.description = description;
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class TransitionResult<S extends State<T>, T, E extends Event<F>, F, R> extends Result<R> {

		@NonNull
		E event;

		@Nullable
		List<S> states;

		@Nullable
		Transition<S, T, E, F, R> transition;

		@Nullable
		List<Result<R>> taskResults;

		public TransitionResult(@NonNull UUID uuid, @NonNull R value, @NonNull String description, @NonNull E event,
				@Nullable List<S> states, @Nullable Transition<S, T, E, F, R> transition,
				@Nullable List<Result<R>> taskResults) {
			super(uuid, value, description);
			this.event = event;
			this.states = states;
			this.transition = transition;
			this.taskResults = taskResults;
		}
	}

	public sealed interface TransitionExecutor<S extends State<T>, T, E extends Event<F>, F, R>
			permits DefaultTransitionExecutorImpl {

		public TransitionResult<S, T, E, F, R> run(@NonNull UUID uuid,
				@NonNull S state, @NonNull E event,
				@NonNull Transition<S, T, E, F, R> transition,
				@NonNull Many<Change<S>> stateSink);
	}

	public sealed interface TaskExecutor<S extends State<T>, T, E extends Event<F>, F, R>
			extends AutoCloseable
			permits ParallelFAETaskExecutorImpl, SequentialFAETaskExecutorImpl {

		public List<Result<R>> run(@NonNull S state, @NonNull E event,
				@NonNull List<BiFunction<S, E, Result<R>>> tasks,
				@Nullable Duration timeout);
	}
}
