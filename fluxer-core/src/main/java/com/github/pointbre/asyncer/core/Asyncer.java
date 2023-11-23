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

public interface Asyncer<S, E> extends AutoCloseable {

    Mono<TransitionResult<S, E>> fire(@NonNull UUID uuid, @NonNull E event);

    Flux<S> state();

    @Value
    public class Transition<S, E> {

	@NonNull
	String name;

	@NonNull
	S from;

	@NonNull
	E event;
	
	@Nullable
	S to;

	@Nullable
	List<Callable<TaskResult>> tasks;

	@Nullable
	Class<? extends TaskExecutor> taskExecutor;

	@Nullable
	Duration timeout;
	
	@Nullable
	S toWhenProcessed;

	@Nullable
	S toWhenFailed;
    }

    @Value
    public class TransitionResult<S, E> {

	@NonNull
	UUID uuid;

	@NonNull
	E event;

	@NonNull
	List<S> states;

	@Nullable
	Transition<S, E> transition;

	@Nullable
	List<TaskResult> taskResults;

	@NonNull
	Boolean result;

	@NonNull
	String description;

    }

    @Value
    public class TaskResult {

	@NonNull
	UUID uuid;

	@NonNull
	Boolean result;

	@NonNull
	String description;

    }

    public sealed interface TransitionExecutor<S, E> extends AutoCloseable permits DefaultTransitionExecutorImpl {

	public TransitionResult<S, E> run(@NonNull UUID uuid, @NonNull S state, @NonNull E event, @NonNull Transition<S, E> transition, @NonNull Many<S> stateSink);

	public static <S, E> TransitionExecutor<S, E> of(@NonNull Class<? extends TransitionExecutor<S, E>> executor) {
	    return new DefaultTransitionExecutorImpl<>();
	}

    }

    public sealed interface TaskExecutor extends AutoCloseable
	    permits ParallelFAETaskExecutor, SequentialFAETaskExecutor {

	public List<TaskResult> run(@NonNull List<Callable<TaskResult>> tasks, @Nullable Duration timeout);

	public static TaskExecutor of(@NonNull Class<? extends TaskExecutor> taskExecutor) {

	    if (taskExecutor.equals(ParallelFAETaskExecutor.class)) {
		return new ParallelFAETaskExecutor();
	    } else if (taskExecutor.equals(SequentialFAETaskExecutor.class)) {
		return new SequentialFAETaskExecutor();
	    }
	    return new ParallelFAETaskExecutor();

	}

    }

}
