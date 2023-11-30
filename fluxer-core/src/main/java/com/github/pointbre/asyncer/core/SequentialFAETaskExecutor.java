package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope.ShutdownOnSuccess;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import lombok.NonNull;
import reactor.util.annotation.Nullable;

public non-sealed class SequentialFAETaskExecutor<S extends State<S>, E extends Event<E>>
		implements TaskExecutor<S, E, Boolean> {

	private final List<TaskResult<Boolean>> taskResults = new ArrayList<>();
	private final List<ShutdownOnSuccess<TaskResult<Boolean>>> scopes = new ArrayList<>();

	@Override
	public List<TaskResult<Boolean>> run(@NonNull S state, @NonNull E event,
			@NonNull List<BiFunction<S, E, TaskResult<Boolean>>> tasks,
			@Nullable Duration timeout) {

		for (BiFunction<S, E, TaskResult<Boolean>> task : tasks) {
			try (ShutdownOnSuccess<TaskResult<Boolean>> scope = new ShutdownOnSuccess<>()) {

				scopes.add(scope);

				scope.fork(() -> task.apply(state, event));

				boolean isTimedOut = false;
				if (timeout == null) {
					try {
						scope.join();
					} catch (InterruptedException e) {
						System.out.println("111 Interrupted called");
						return taskResults;
					}
				} else {
					try {
						scope.joinUntil(Instant.now().plus(timeout));
					} catch (InterruptedException e) {
						System.out.println("222 Interrupted called");
						return taskResults;
					} catch (TimeoutException e) {
						isTimedOut = true;
					}
				}

				if (isTimedOut) {
					taskResults.add(new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE,
							"Task execution timed out: " + task));
				} else {
					try {
						taskResults.add(scope.result());
					} catch (Exception e) {
						taskResults.add(new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE,
								"Failed to get the result of the task " + task + " : " + e.getLocalizedMessage()));
					}
				}
			}
		}

		return taskResults;
	}

	@Override
	public void close() throws Exception {

		System.out.println("SequentialFailAtEndTaskExecutor's close() called");

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
