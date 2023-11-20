package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.github.pointbre.asyncer.core.Asyncer.TaskResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public interface Asyncer extends AutoCloseable {

    Mono<TransitionResult> fire(@NonNull UUID uuid, @NonNull Event event);

    Flux<State> state();

    @Value
    public class State {

	@NonNull
	String name;

    }

    @Value
    public class Event {

	@NonNull
	String name;

    }

    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @Getter
    public sealed class Transition permits StaticTransition, DynamicTransition {

	@NonNull
	String name;

	@NonNull
	State from;

	@NonNull
	Event event;

	@Nullable
	List<Callable<TaskResult>> tasks;

	@Nullable
	Class<? extends TaskExecutor> taskExecutor;

	@Nullable
	Duration timeout;
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public class StaticTransition extends Transition {

	@Nullable
	State to;

	public StaticTransition(@NonNull String name, @NonNull State from, @NonNull Event event,
		@Nullable List<Callable<TaskResult>> tasks, @Nullable Class<? extends TaskExecutor> taskExecutor,
		@Nullable Duration timeout, @Nullable State to) {

	    super(name, from, event, tasks, taskExecutor, timeout);
	    this.to = to;

	}

    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public class DynamicTransition extends Transition {

	@NonNull
	State toWhenProcessed;

	@NonNull
	State toWhenFailed;

	public DynamicTransition(@NonNull String name, @NonNull State from, @NonNull Event event,
		@NonNull List<Callable<TaskResult>> tasks, @NonNull Class<? extends TaskExecutor> taskExecutor,
		@Nullable Duration timeout, @NonNull State toWhenProcessed, @NonNull State toWhenFailed) {

	    super(name, from, event, tasks, taskExecutor, timeout);
	    this.toWhenProcessed = toWhenProcessed;
	    this.toWhenFailed = toWhenFailed;

	}

    }

    @Value
    public class TransitionResult {

	@NonNull
	UUID uuid;

	@NonNull
	Event event;

	@NonNull
	State fromState;

	@Nullable
	State toState;

	@Nullable
	Transition transition;

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

    public interface TransitionExecutor extends AutoCloseable {

	public TransitionResult run(@NonNull UUID uuid, @NonNull Event event, @NonNull Transition transition);

	public static TransitionExecutor of(@NonNull Class<? extends TransitionExecutor> executor) {
	    return new DefaultTransitionExecutorImpl();
	}

    }

    // FIXME FailNever/FailFast/FailAtEnd, Sequential/Parallel
    public sealed interface TaskExecutor extends AutoCloseable
	    permits ParallelFailAtEndActionExecutor, SequentialFailAtEndActionExecutor {

	public List<TaskResult> run(@NonNull List<Callable<TaskResult>> tasks, @Nullable Duration timeout);

	public static TaskExecutor of(@NonNull Class<? extends TaskExecutor> taskExecutor) {

	    if (taskExecutor.equals(ParallelFailAtEndActionExecutor.class)) {
		return new ParallelFailAtEndActionExecutor();
	    } else if (taskExecutor.equals(SequentialFailAtEndActionExecutor.class)) {
		return new SequentialFailAtEndActionExecutor();
	    }
	    return new ParallelFailAtEndActionExecutor();

	}

    }

}
