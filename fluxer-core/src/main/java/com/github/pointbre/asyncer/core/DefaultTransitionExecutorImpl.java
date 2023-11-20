package com.github.pointbre.asyncer.core;

import java.util.List;
import java.util.UUID;

import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.StaticTransition;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;

import lombok.NonNull;

public class DefaultTransitionExecutorImpl implements TransitionExecutor {

    private TaskExecutor taskExecutor = null;
    
    @Override
    public TransitionResult run(@NonNull UUID uuid, @NonNull Event event, @NonNull Transition transition) {

	State fromState = transition.getFrom();
	State toState = fromState;
	if (transition instanceof StaticTransition t) {
	    toState = t.getTo();
	}

	// FIXME Probably better to put this block as a common validation util that can be used in DefaultAsyncerImpl's constructor
	if (transition instanceof StaticTransition t) {
	    if (t.getTasks() == null || t.getTasks().isEmpty()) {
		return new TransitionResult(uuid, event, fromState, toState, t, null, Boolean.TRUE,
			"No action to run is specified for static transition: " + t);
	    } else if (t.getTaskExecutor() == null) {
		return new TransitionResult(uuid, event, fromState, toState, t, null, Boolean.TRUE,
			"No task executor is provided for static transition: " + t);
	    }
	} else if (transition instanceof DynamicTransition t) {
	    if (t.getTasks() == null || t.getTasks().isEmpty()) {
		return new TransitionResult(uuid, event, fromState, toState, t, null, Boolean.FALSE,
			"No action to run is specified for dynamic transition: " + t);
	    } else if (t.getTaskExecutor() == null) {
		return new TransitionResult(uuid, event, fromState, toState, t, null, Boolean.FALSE,
			"No task executor is provided for dynamic transition: " + t);
	    }
	}

	taskExecutor = TaskExecutor.of(transition.getTaskExecutor());
	List<TaskResult> taskResults = taskExecutor.run(transition.getTasks(), transition.getTimeout());
	if (transition instanceof DynamicTransition t) {
	    if (transition.getTasks().size() == taskResults.size()
		    && taskResults.stream().allMatch(r -> r.getResult().booleanValue())) {
		toState = t.getToWhenProcessed();
	    } else {
		toState = t.getToWhenFailed();
	    }
	}

	return new TransitionResult(uuid, event, fromState, toState, transition, taskResults, Boolean.TRUE,
		"Successfully executed the transition");
    }

    @Override
    public void close() throws Exception {
	
	if (taskExecutor != null) {
	    taskExecutor.close();
	}
	
    }
}
