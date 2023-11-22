package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.StaticTransition;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;

@ExtendWith(MockitoExtension.class)
public class AsyncerTest {
    @Test
    void test() throws Exception {

	var locked = new State("locked");
	var unlocked = new State("unlocked");
	var states = new HashSet<State>();
	states.add(locked);
	states.add(unlocked);

	var coin = new Event("Coin");
	var push = new Event("Push");
	var events = new HashSet<Event>();
	events.add(coin);
	events.add(push);

//	var lockedToUnlocked = new Asyncer.StaticTransition(locked, coin, null, unlocked);
	List<Callable<TaskResult>> tasks1 = new ArrayList<>(Arrays.asList(
		() -> {
		    System.out.println(">>>1 task #1 of action");
		    System.out.println(">>>1 Taking 6 seconds sleep");
//		    try {
		    Thread.sleep(Duration.ofSeconds(6));
//		    } catch (InterruptedException e) {	
//			System.out.println(">>>1 task #1 interrupted");
//		    }
		    System.out.println(">>>1 Will return result now");
		    return new TaskResult(AsyncerUtil.generateType1UUID(), Boolean.TRUE, "done 1");
		},

		() -> {
		    System.out.println(">>>2 task #2 of action");
		    System.out.println(">>>2 Taking 3 seconds sleep");
//		    try {
		    Thread.sleep(Duration.ofSeconds(3));
//		    } catch (InterruptedException e) {		
//			e.printStackTrace();
//		    }
		    System.out.println(">>>2 Will return result now");
		    return new TaskResult(AsyncerUtil.generateType1UUID(), Boolean.TRUE, "done 2");
		}));

	var lockedToUnlocked = new DynamicTransition("", locked, coin, tasks1, ParallelFAETaskExecutor.class,
		null, unlocked, locked);

	var lockedToLocked = new StaticTransition("", locked, push, null, null, null, locked);

	// var unlockedToLocked = new Asyncer.StaticTransition(unlocked, push, null,
	// locked);
	List<Callable<TaskResult>> tasks2 = new ArrayList<>(Arrays.asList(
		() -> {
		    System.out.println(">>>3 task #1 of action");
		    System.out.println(">>>3 Taking 3 seconds sleep");
		    Thread.sleep(Duration.ofSeconds(3));
		    System.out.println(">>>3 Will return result now");
		    return new TaskResult(AsyncerUtil.generateType1UUID(), Boolean.TRUE, "done 3");
		}));
	var unlockedToLocked = new StaticTransition("", unlocked, push, tasks2, ParallelFAETaskExecutor.class,
		null, locked);

	var unlockedToUnlocked = new StaticTransition("", unlocked, coin, null, null, null, unlocked);

	var transitions = new HashSet<Asyncer.Transition>();
	transitions.add(lockedToUnlocked);
	transitions.add(lockedToLocked);
	transitions.add(unlockedToLocked);
	transitions.add(unlockedToUnlocked);

	CountDownLatch latch = new CountDownLatch(1);
	TransitionExecutor transitionExecutor = new DefaultTransitionExecutorImpl();
	long time1 = System.currentTimeMillis();
	try (var asyncer = new DefaultAsyncerImpl(states, locked, null, events, transitions, transitionExecutor)) {

	    System.out.println("1");

	    asyncer.state().subscribe(s -> {
		System.out.println("state updated=" + s);
	    }, e -> {
		System.out.println("state error=" + e);
	    });

	    System.out.println("2");

	    
	    asyncer.fire(AsyncerUtil.generateType1UUID(), coin).subscribe(r -> {
		System.out.println("fire coin finished=" + r);
		latch.countDown();
	    }, e -> {
		System.out.println("fire coin error=" + e);
		latch.countDown();
	    });

	    System.out.println("3");

//	    asyncer.fire(AsyncerUtil.generateType1UUID(), push).subscribe(r -> {
//		System.out.println("fire push 1 finished=" + r);
//		latch.countDown();
//	    }, e -> {
//		System.out.println("fire push 1 error=" + e);
//		latch.countDown();
//	    });
//
//	    System.out.println("4");

	    try {
		latch.await(5, TimeUnit.SECONDS);
		long time2 = System.currentTimeMillis();
		System.out.println("5 --> " + (time2 - time1));
//		latch.await();
	    } catch (InterruptedException e) {

	    } finally {

	    }

	    System.out.println("6");
	}

//	Thread.sleep(5);
	long time3 = System.currentTimeMillis();
	System.out.println("7 --> " + (time3 - time1));
    }
}
