package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import lombok.NonNull;
import reactor.util.annotation.Nullable;

// FIXME don't extend StructuredTaskScope. Include task scope and use it just like SequentialFAETaskExecutor
public non-sealed class ParallelFAETaskExecutor<S extends State<S>, E extends Event<E>>
		extends StructuredTaskScope<TaskResult<Boolean>>
		implements TaskExecutor<S, E, Boolean> {

	private final Queue<TaskResult<Boolean>> taskResults = new LinkedTransferQueue<>();

	@Override
	public List<TaskResult<Boolean>> run(@NonNull S state, @NonNull E event,
			@NonNull List<BiFunction<S, E, TaskResult<Boolean>>> tasks,
			@Nullable Duration timeout) {

		tasks.stream().forEach(task -> fork(() -> task.apply(state, event)));

		if (timeout == null) {
			try {
				join();
			} catch (InterruptedException e) {
				System.out.println("111 Interrupted called: " + taskResults);
			}

		} else {
			try {
				joinUntil(Instant.now().plus(timeout));
			} catch (InterruptedException e) {
				System.out.println("222 Interrupted called: " + taskResults);
			} catch (TimeoutException e) {
				System.out.println("222 Timed out");
			}
		}

		return taskResults.stream().collect(Collectors.toUnmodifiableList());
	}

	@Override
	protected void handleComplete(Subtask<? extends TaskResult<Boolean>> task) {
		if (task.state() == Subtask.State.FAILED) {
			System.out.println("FAILED " + task);
			taskResults.add(new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE,
					"Exception occurred: " + task.exception()));
		} else if (task.state() == Subtask.State.UNAVAILABLE) {
			System.out.println("UNAVAILABLE " + task);
			taskResults
					.add(new TaskResult<>(AsyncerUtil.generateType1UUID(), Boolean.FALSE,
							"Not completed after forked"));
		} else {
			System.out.println("SUCCESS " + task);
			taskResults.add(task.get());
		}
	}

	@Override
	public void close() {

		System.out.println("ParallelFailAtEndTaskExecutor's close() called");
		super.close();

	}

}