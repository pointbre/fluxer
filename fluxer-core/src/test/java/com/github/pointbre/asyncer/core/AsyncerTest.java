package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;

@ExtendWith(MockitoExtension.class)
public class AsyncerTest {

	public enum State {
		STARTING, STARTED, STOPPING, STOPPED
	}

	public enum Event {
		START, STOP;
	}

	@Test
	void test() throws Exception {

		// var lockedToUnlocked = new Asyncer.StaticTransition(locked, coin, null,
		// unlocked);
		List<Callable<TaskResult<Boolean>>> tasks1 = new ArrayList<>(Arrays.asList(
				() -> {
					Thread.sleep(Duration.ofSeconds(3));
					return new TaskResult(AsyncerUtil.generateType1UUID(), Boolean.TRUE, "done");
				}));

		var stoppedToStartingAndThenStartedOrStopped = new Transition<State, Event, Boolean>("", State.STOPPED,
				Event.START,
				State.STARTING, tasks1, SequentialFAETaskExecutor.class,
				null, State.STARTED, State.STOPPED);

		Set<Transition<State, Event, Boolean>> transitions = new HashSet<>();
		transitions.add(stoppedToStartingAndThenStartedOrStopped);

		CountDownLatch latch = new CountDownLatch(1);
		TransitionExecutor<State, Event, Boolean> transitionExecutor = new DefaultTransitionExecutorImpl<>();
		long time1 = System.currentTimeMillis();
		try (Asyncer<State, Event, Boolean> asyncer = new DefaultAsyncerImpl<>(State.STOPPED, null, transitions,
				transitionExecutor)) {

			System.out.println("1");

			asyncer.stateChange().subscribe(s -> {
				System.out.println("state updated=" + s + " at " + new Date());
			}, e -> {
				System.out.println("state error=" + e);
			});

			System.out.println("2");

			asyncer.fire(AsyncerUtil.generateType1UUID(), Event.START).subscribe(r -> {
				System.out.println("fire START finished=" + r);
				latch.countDown();
			}, e -> {
				System.out.println("fire START error=" + e);
				latch.countDown();
			});

			System.out.println("3");

			// asyncer.fire(AsyncerUtil.generateType1UUID(), push).subscribe(r -> {
			// System.out.println("fire push 1 finished=" + r);
			// latch.countDown();
			// }, e -> {
			// System.out.println("fire push 1 error=" + e);
			// latch.countDown();
			// });
			//
			// System.out.println("4");

			try {
				latch.await(10, TimeUnit.SECONDS);
				long time2 = System.currentTimeMillis();
				System.out.println("5 --> " + (time2 - time1));
				// latch.await();
			} catch (InterruptedException e) {

			} finally {

			}

			System.out.println("6");
		}

		// Thread.sleep(5);
		long time3 = System.currentTimeMillis();
		System.out.println("7 --> " + (time3 - time1));
	}
}
