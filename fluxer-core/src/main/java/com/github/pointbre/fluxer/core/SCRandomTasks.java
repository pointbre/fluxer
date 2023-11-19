package com.github.pointbre.fluxer.core;

import java.util.Random;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.stream.IntStream;

public class SCRandomTasks {

    class TooSlowException extends Exception {
	public TooSlowException(String s) {
	    super(s);
	}
    }

    public Integer randomTask(int maxDuration, int threshold) throws InterruptedException, TooSlowException {
	int t = new Random().nextInt(maxDuration);
	System.out.println("Duration: " + t);
	if (t > threshold) {
	    throw new TooSlowException("Duration " + t + " greater than threshold " + threshold);
	}
	Thread.sleep(t);
	return Integer.valueOf(t);
    }

    void handleBoth() throws InterruptedException {
	try (var scope = new CollectingScope()) {
	    // var t = new SCRandomTasks();
	    var subtasks = IntStream.range(0, 5)
		    .mapToObj(i -> scope.fork(() -> randomTask(1000, 500)))
		    .toList();
	    scope.join();

	    var totalDuration = scope.successfulTasks()
		    .mapToInt(st -> (Integer) ((Subtask) st).get())
		    .reduce(0, Integer::sum);
	    System.out.println("Total duration: " + totalDuration);

	    scope.failedTasks()
		    .forEach(ft -> System.out.println(((Exception) ((Subtask) ft).exception()).getMessage()));
	}
    }

    public static void main(String[] args) {
	var myApp = new SCRandomTasks();
	try {
	    System.out.println("Running handleBoth...");
	    myApp.handleBoth();
	} catch (Exception e) {
	    System.out.println(e.getMessage());
	}
    }
}
