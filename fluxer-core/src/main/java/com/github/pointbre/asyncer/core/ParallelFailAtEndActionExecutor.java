package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;

import lombok.NonNull;

public non-sealed class ParallelFailAtEndActionExecutor extends StructuredTaskScope<TaskResult> implements TaskExecutor {

    private final Queue<Subtask<? extends TaskResult>> executedSubtasks = new LinkedTransferQueue<>();
    private final List<TaskResult> taskResults = new ArrayList<>();

    @Override
    public List<TaskResult> run(@NonNull List<Callable<TaskResult>> tasks, Duration timeout) {

	tasks.stream().forEach(task -> fork(task));

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
	    if (isAllExecutedSuccessfully(action.getTasks(), executedSubtasks)) {
		toState = t.getToWhenProcessed();
	    } else {
		toState = t.getToWhenFailed();
	    }
	}

	List<TaskResult> taskResults = executedSubtasks.stream().map(task -> task.get()).collect(Collectors.toList());

	return new TransitionResult(uuid, event, fromState, toState, transition, taskResults,
		Boolean.TRUE, "Successfully execute transition");
    }
    
    @Override
    protected void handleComplete(Subtask<? extends TaskResult> task) {
	executedSubtasks.add(task);
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