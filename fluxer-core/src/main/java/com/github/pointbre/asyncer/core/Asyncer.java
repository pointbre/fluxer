package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope.Subtask;

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

    Mono<TransitionResult> fire(UUID uuid, Event event);

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

    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public class StaticTransition extends Transition {

	@Nullable
	Action action;

	@Nullable
	State to;

	public StaticTransition(@NonNull String name, @NonNull State from, @NonNull Event event, Action action,
		State to) {
	    super(name, from, event);
	    this.action = action;
	    this.to = to;
	}

    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public class DynamicTransition extends Transition {
	@NonNull
	Action action;

	@NonNull
	State toWhenProcessed;

	@NonNull
	State toWhenFailed;

	public DynamicTransition(@NonNull String name, @NonNull State from, @NonNull Event event,
		@NonNull Action action,
		@NonNull State toWhenProcessed, @NonNull State toWhenFailed) {
	    super(name, from, event);
	    this.action = action;
	    this.toWhenProcessed = toWhenProcessed;
	    this.toWhenFailed = toWhenFailed;
	}

    }

    @Value
    public class Action {

	@NonNull
	String name;

	@NonNull
	List<Callable<TaskResult>> tasks;

	@NonNull
	Class<? extends Executor> executor;

	@Nullable
	Duration timeout;

    }

    @Value
    public class TransitionResult {

	@NonNull
	UUID uuid;

	@NonNull
	Event event;

	@Nullable
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
	Boolean result;

	@NonNull
	String description;
    }

    // FIXME FailNever, FailFast, FailAtEnd
    public sealed interface Executor extends AutoCloseable permits ParallelFailAtEndExecutor, SequentialFailAtEndExecutor {
	public TransitionResult run(UUID uuid, Event event, Transition transition);
	
	static Executor of(Class<? extends Executor> executor) {
	    if (executor.equals(ParallelFailAtEndExecutor.class)) {
		return new ParallelFailAtEndExecutor();
	    }
	    else if (executor.equals(SequentialFailAtEndExecutor.class)) {
		return new SequentialFailAtEndExecutor();
	    }
	    return new ParallelFailAtEndExecutor();
	}
    }
//    public abstract sealed class Executor extends StructuredTaskScope<TaskResult> permits FailAtEndExecutor {
//	public abstract TransitionResult run(UUID uuid, Event event, Transition transition);
//	
//	static Executor of(Class<? extends Executor> executor) {
//	    if (executor.equals(FailAtEndExecutor.class)) {
//		return new FailAtEndExecutor();
//	    }
//	    return new FailAtEndExecutor();
//	}
//
//	static boolean isExecutedSuccessfully(Subtask<? extends TaskResult> executedTask) {
//	    return executedTask.state().equals(Subtask.State.SUCCESS) && executedTask.get().getResult().booleanValue();
//	}
//
//	static boolean isAllExecutedSuccessfully(List<Callable<TaskResult>> tasksToExecute,
//		Queue<Subtask<? extends TaskResult>> executedTasks) {
//	    return tasksToExecute.size() == executedTasks.size()
//		    && executedTasks.stream().allMatch(task -> isExecutedSuccessfully(task));
//	}
//    }

}
