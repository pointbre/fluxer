package com.github.pointbre.asyncer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.NonNull;
import reactor.core.publisher.Sinks.Many;

public non-sealed class DefaultTransitionExecutorImpl<S extends State<S>, E extends Event<E>>
		implements TransitionExecutor<S, E, Boolean> {

	private TaskExecutor<S, E, Boolean> taskExecutor = null;

	@Override
	public TransitionResult<S, E, Boolean> run(@NonNull UUID uuid, @NonNull S state, @NonNull E event,
			@NonNull Transition<S, E, Boolean> transition, @NonNull Many<StateChange<S>> stateSink) {

		List<S> states = new ArrayList<>();
		List<TaskResult<Boolean>> taskResults = null;

		if (transition.getTo() != null) {
			S firstState = transition.getTo();
			states.add(firstState);

			try {
				stateSink.tryEmitNext(new StateChange<>(AsyncerUtil.generateType1UUID(), firstState));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (transition.getTasks() != null && !transition.getTasks().isEmpty()
				&& transition.getTaskExecutor() != null) {
			taskResults = transition.getTaskExecutor().run(state, event, transition.getTasks(),
					transition.getTimeout());
			if (transition.getToWhenProcessed() != null
					&& transition.getToWhenFailed() != null) {
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

	@Override
	public void close() throws Exception {

		if (taskExecutor != null) {
			taskExecutor.close();
		}

	}
}
