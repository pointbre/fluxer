package com.github.pointbre.asyncer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.pointbre.asyncer.core.Asyncer.StateChange;
import com.github.pointbre.asyncer.core.Asyncer.TaskExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;

import lombok.NonNull;
import reactor.core.publisher.Sinks.Many;

public non-sealed class DefaultTransitionExecutorImpl<S, E, R> implements TransitionExecutor<S, E, Boolean> {

	private TaskExecutor<R> taskExecutor = null;

	@Override
	public TransitionResult<S, E, Boolean> run(@NonNull UUID uuid, @NonNull S state, @NonNull E event,
			@NonNull Transition<S, E, Boolean> transition, @NonNull Many<StateChange<S>> stateSink) {

		boolean isToStateSpecified = transition.getTo() != null;
		boolean shouldRunTasks = transition.getTasks() != null && !transition.getTasks().isEmpty()
				&& transition.getTaskExecutor() != null;
		boolean shouldDetermineStateFromTaskResults = shouldRunTasks && transition.getToWhenProcessed() != null
				&& transition.getToWhenFailed() != null;

		List<S> states = new ArrayList<>();
		List<TaskResult<Boolean>> taskResults = null;

		if (isToStateSpecified) {
			S firstState = transition.getTo();
			states.add(firstState);

			try {
				stateSink.tryEmitNext(new StateChange<>(AsyncerUtil.generateType1UUID(), firstState));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (shouldRunTasks) {
			taskResults = getTaskExecutor(transition.getTaskExecutor()).run(transition.getTasks(),
					transition.getTimeout());
			if (shouldDetermineStateFromTaskResults) {
				S secondState = transition.getToWhenFailed();
				if (transition.getTasks().size() == taskResults.size()
						&& taskResults.stream().allMatch(r -> r.getResult().booleanValue())) {
					secondState = transition.getToWhenProcessed();
				}
				states.add(secondState);

				try {
					stateSink.tryEmitNext(new StateChange<>(AsyncerUtil.generateType1UUID(), secondState));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return new TransitionResult<>(uuid, event, states, transition, taskResults, Boolean.TRUE,
				"Successfully executed the transition");
	}

	private TaskExecutor<Boolean> getTaskExecutor(Class<? extends TaskExecutor<Boolean>> taskExecutor) {
		if (taskExecutor != null) {
			if (taskExecutor.equals(ParallelFAETaskExecutor.class)) {
				return new ParallelFAETaskExecutor();
			} else if (taskExecutor.equals(SequentialFAETaskExecutor.class)) {
				return new SequentialFAETaskExecutor();
			}
		}
		return new SequentialFAETaskExecutor();
	}

	@Override
	public void close() throws Exception {

		if (taskExecutor != null) {
			taskExecutor.close();
		}

	}
}
