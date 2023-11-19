package com.github.pointbre.fluxer.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Stream;

public class StructuredConcurrencyDemo {

//    private static final ScopedValue<String> CONTEXT = ScopedValue.newInstance();
    
    public record MyContext (String v1, String v2) { }
//
    private static final ScopedValue<MyContext> CONTEXT = ScopedValue.newInstance();
//


    public static void main(String[] args) {

//	ScopedValue.runWhere(CONTEXT, "Test Value", () -> {
//	    System.out.println("In parent thread start the scoped value is: " + CONTEXT.get());
//	    doSomething();
//	    System.out.println("In parent thread end the scoped value is: " + CONTEXT.get());
//	  });
//
//	System.out.println("Forking new threads...");
	
	ScopedValue.runWhere(CONTEXT, new MyContext("1", "2"), () -> {
	    System.out.println(CONTEXT.get().v1() +"," + CONTEXT.get().v2());
	});

	var id = 100L;

	try (var scope = new MyScope<DemographicData>()) {

	    scope.fork(() -> fetchUserDetails(id));
	    scope.fork(() -> fetchUserDetailsNew(id));

	    scope.join();

	    scope.results().forEach(System.out::println);
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	
//	var scope = new StructuredTaskScope.ShutdownOnFailure();
//	scope.join().throwIfFailed();
	
//	ScopedValue.runWhere(MULTIPLIER, number, () -> {
//		try (var scope = new StructuredTaskScope<BigInteger>()) {
//
//			scope.fork(() -> squaredByGet());
//			scope.fork(() -> squaredByGet());
//			scope.fork(() -> squaredByGet());
//
//		}
//	});
	
//	try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
//
//	    scope.fork(() -> fetchUserDetails(id));
//	    scope.fork(() -> fetchUserDetailsNew(id));
//
//	    scope.join();
//
//	    scope.results().forEach(System.out::println);
//	} catch (Exception e) {
//	    throw new RuntimeException(e);
//	}
    }

    private static DemographicData fetchUserDetails(Long id) throws InterruptedException {
	Thread.sleep(2000L);
	System.out.println("Retrieved DemographicData from fetchUserDetails");
	return new DemographicData("1");
//	throw new Exception("My exception");
    }

    private static DemographicData fetchUserDetailsNew(Long id) throws InterruptedException {
	Thread.sleep(1000L);
	System.out.println("Retrieved DemographicData from fetchUserDetailsNew");
	return new DemographicData("2");
    }

//    public static void doSomething() {
//	System.out.println("In doSomething() and parent scope: " + CONTEXT.get());
//	ScopedValue.runWhere(CONTEXT, "Changed Value", () -> {
//	    System.out.println("In doSomething() and child scope: " + CONTEXT.get());
//	    doSomethingAgain();
//	});
//    }

    public static void doSomethingAgain() {
	System.out.println("In doSomethingAgain() and child scope: " + CONTEXT.get());
    }
}
