package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.ShutdownOnSuccess;
import java.util.concurrent.TimeoutException;

import com.github.pointbre.asyncer.core.Asyncer.Action;
import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.Executor;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.StaticTransition;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;

public non-sealed class SequentialFailAtEndExecutor implements Executor {
    private final List<TaskResult> taskResults = new ArrayList<>();
    private final List<ShutdownOnSuccess<TaskResult>> scopes = new ArrayList<>();

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

	for (Callable<TaskResult> task : action.getTasks()) {
	    try (ShutdownOnSuccess<TaskResult> scope = new ShutdownOnSuccess<>()) {
		scopes.add(scope);

		scope.fork(task);

		Duration timeout = action.getTimeout();
		boolean isTimedOut = false;
		if (timeout == null) {
		    try {
			scope.join();
		    } catch (InterruptedException e) {
			return new TransitionResult(uuid, event, fromState, toState, transition, null,
				Boolean.FALSE, "Execution of tasks has been interrupted: " + action.getTasks());
		    }
		} else {
		    try {
			scope.joinUntil(Instant.now().plus(timeout));
		    } catch (InterruptedException e) {
			return new TransitionResult(uuid, event, fromState, toState, transition, null,
				Boolean.FALSE, "Execution of tasks has been interrupted: " + action.getTasks());
		    } catch (TimeoutException e) {
			isTimedOut = true;
		    }
		}

		if (isTimedOut) {
		    taskResults.add(new TaskResult(Boolean.FALSE, "Execution timeout:" + task));
		} else {
		    try {
			taskResults.add((TaskResult) scope.result());
		    } catch (Exception e) {
			taskResults.add(new TaskResult(Boolean.FALSE,
				"Failed to execute the task " + task + " : " + e.getLocalizedMessage()));
		    }
		}
	    }
	}

	if (transition instanceof DynamicTransition t) {
	    if (taskResults.size() == action.getTasks().size()
		    && taskResults.stream().allMatch(result -> result.getResult().booleanValue())) {
		toState = t.getToWhenProcessed();
	    } else {
		toState = t.getToWhenFailed();
	    }
	}
	return new TransitionResult(uuid, event, fromState, toState, transition, taskResults,
		Boolean.TRUE, "Successfully execute transition");
    }

    @Override
    public void close() throws Exception {
	System.out.println("SequentialFailAtEndExecutor's close() called");

	scopes.forEach(scope -> {
	    if (!scope.isShutdown()) {
		System.out.println("Closing " + scope);
		try {
		    scope.close();
		} catch (Exception e) {
		}
	    }
	});
    }
}
