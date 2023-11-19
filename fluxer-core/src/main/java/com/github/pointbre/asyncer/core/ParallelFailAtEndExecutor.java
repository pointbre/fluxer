package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.stream.Collectors;

import com.github.pointbre.asyncer.core.Asyncer.Action;
import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Executor;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.StaticTransition;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;

public non-sealed class ParallelFailAtEndExecutor extends StructuredTaskScope<TaskResult> implements Executor {

    private final Queue<Subtask<? extends TaskResult>> executedTasks = new LinkedTransferQueue<>();

    @Override
    protected void handleComplete(Subtask<? extends TaskResult> task) {
	executedTasks.add(task);
    }

    @Override
    public TransitionResult run(UUID uuid, Event event, Transition transition) {
	State fromState = transition.getFrom();
	State toState = fromState;
	if (transition instanceof StaticTransition t) {
	    toState = t.getTo();
	}

	Action action = null;
	if (transition instanceof StaticTransition t) {
	    action = t.getAction();
	    if (action == null) {
		return new TransitionResult(uuid, event, fromState, toState, transition,
			null, Boolean.TRUE, "No action to run: " + transition);
	    } else if (action.getTasks() == null || action.getTasks().isEmpty()) {
		return new TransitionResult(uuid, event, fromState, toState, transition, null,
			Boolean.TRUE, "No tasks of action of static transition to run: " + action);
	    }
	} else if (transition instanceof DynamicTransition t) {
	    action = t.getAction();
	    if (action == null) {
		return new TransitionResult(uuid, event, fromState, toState, transition, null,
			Boolean.FALSE, "Action of dynamic transition shouldn't be null: " + transition);
	    } else if (action.getTasks() == null || action.getTasks().isEmpty()) {
		return new TransitionResult(uuid, event, fromState, toState, transition, null,
			Boolean.FALSE,
			"The tasks of action of dynamic transition shouldn't be null or empty: " + action);
	    }
	}

	action.getTasks().stream().forEach(task -> fork(task));

	Duration timeout = action.getTimeout();
	if (timeout == null) {
	    try {
		join();
	    } catch (InterruptedException e) {
		System.out.println("111 Interrupted called");
		return new TransitionResult(uuid, event, fromState, toState, transition, null,
			Boolean.FALSE, "Execution of tasks has been interrupted: " + action.getTasks());
	    }

	} else {
	    try {
		joinUntil(Instant.now().plus(timeout));
	    } catch (InterruptedException e) {
		System.out.println("222 Interrupted called");
		return new TransitionResult(uuid, event, fromState, toState, transition, null,
			Boolean.FALSE, "Execution of tasks has been interrupted: " + action.getTasks());
	    } catch (TimeoutException e) {
		System.out.println("222 Timed out");
		return new TransitionResult(uuid, event, fromState, toState, transition, null,
			Boolean.FALSE, "Execution of tasks has been timed out: " + action.getTasks());
	    }
	}

	try {
	    ensureOwnerAndJoined();
	} catch (Exception e) {
	    return new TransitionResult(uuid, event, fromState, toState, transition, null,
		    Boolean.FALSE, "Failed to execute tasks: " + e.getLocalizedMessage());
	}

	if (transition instanceof DynamicTransition t) {
	    if (isAllExecutedSuccessfully(action.getTasks(), executedTasks)) {
		toState = t.getToWhenProcessed();
	    } else {
		toState = t.getToWhenFailed();
	    }
	}

	List<TaskResult> taskResults = executedTasks.stream().map(task -> task.get()).collect(Collectors.toList());

	return new TransitionResult(uuid, event, fromState, toState, transition, taskResults,
		Boolean.TRUE, "Successfully execute transition");
    }

    private boolean isExecutedSuccessfully(Subtask<? extends TaskResult> executedTask) {
	return executedTask.state().equals(Subtask.State.SUCCESS) && executedTask.get().getResult().booleanValue();
    }

    private boolean isAllExecutedSuccessfully(List<Callable<TaskResult>> tasksToExecute,
	    Queue<Subtask<? extends TaskResult>> executedTasks) {
	return tasksToExecute.size() == executedTasks.size()
		&& executedTasks.stream().allMatch(task -> isExecutedSuccessfully(task));
    }
}