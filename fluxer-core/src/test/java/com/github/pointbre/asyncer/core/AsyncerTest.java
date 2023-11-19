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

import com.github.pointbre.asyncer.core.Asyncer.Action;
import com.github.pointbre.asyncer.core.Asyncer.DynamicTransition;
import com.github.pointbre.asyncer.core.Asyncer.Event;
import com.github.pointbre.asyncer.core.Asyncer.State;
import com.github.pointbre.asyncer.core.Asyncer.StaticTransition;
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;

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
		    return new TaskResult(Boolean.TRUE, "");
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
		    return new TaskResult(Boolean.TRUE, "");
		}
	));
//	var action1 = new Action("action1", tasks1, FailAtEndExecutor.class, Duration.ofSeconds(2));
//	var action1 = new Action("action1", tasks1, ParallelFailAtEndExecutor.class, null);
	var action1 = new Action("action1", tasks1, SequentialFailAtEndExecutor.class, null);
	var lockedToUnlocked = new DynamicTransition("", locked, coin, action1, unlocked, locked);
	
	var lockedToLocked = new StaticTransition("", locked, push, null, locked);

	//	var unlockedToLocked = new Asyncer.StaticTransition(unlocked, push, null, locked);
	List<Callable<TaskResult>> tasks2 = new ArrayList<>(Arrays.asList(
		() -> {
		    System.out.println(">>>3 task #1 of action");
		    System.out.println(">>>3 Taking 3 seconds sleep");
		    Thread.sleep(Duration.ofSeconds(3));
		    System.out.println(">>>3 Will return result now");
		    return new TaskResult(Boolean.TRUE, "");
		}
	));
	var action2 = new Action("action2", tasks2, ParallelFailAtEndExecutor.class, null);
	var unlockedToLocked = new StaticTransition("", unlocked, push, action2, locked);

	var unlockedToUnlocked = new StaticTransition("", unlocked, coin, null, unlocked);
	var transitions = new HashSet<Asyncer.Transition>();
	transitions.add(lockedToUnlocked);
	transitions.add(lockedToLocked);
	transitions.add(unlockedToLocked);
	transitions.add(unlockedToUnlocked);

	CountDownLatch latch = new CountDownLatch(1);
	try (var asyncer = new AsyncerImpl(states, locked, null, events, transitions)) {
	    
	    System.out.println("1");
	    
	    asyncer.state().subscribe(s -> {
		System.out.println("state updated=" + s);
//		latch.countDown();
	    }, e -> {
		System.out.println("state error=" + e);
//		latch.countDown();
	    });

	    System.out.println("2");

	    long time1 = System.currentTimeMillis();
	    asyncer.fire(AsyncerUtil.generateType1UUID(), coin).subscribe(r -> {
		System.out.println("fire coin finished=" + r);
		latch.countDown();
	    }, e -> {
		System.out.println("fire coin error=" + e);
		latch.countDown();
	    });
	    
	    System.out.println("3");
	    
//	    IntStream.range(1, 10).forEach(n -> {
//		    asyncer.fire(AsyncerUtil.generateType1UUID(), push).subscribe(r -> {
////			System.out.println("fire push " + n + " finished=" + r);
////			latch.countDown();
//		    }, e -> {
////			System.out.println("fire push " + n + " error=" + e);
////			latch.countDown();
//		    });
//	    });
//	    
//	    System.out.println("4");	    
//
	    
	    try {
		latch.await(10, TimeUnit.SECONDS);
		long time2 = System.currentTimeMillis();
		System.out.println("5 --> " + (time2 - time1));
//		latch.await();
	    } catch (InterruptedException e) {

	    } finally {

	    }
	    
	    System.out.println("6");
	}
	
	System.out.println("7");
    }
}
