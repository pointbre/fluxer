package com.github.pointbre.fluxer.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Stream;

public class MyScope<T> extends StructuredTaskScope<T> {

    private final Queue<T> results = new ConcurrentLinkedQueue<>();

    MyScope() {
	super(null, Thread.ofVirtual().factory());
    }

    @Override
    protected void handleComplete(Subtask<? extends T> subtask) {
	if (subtask.state() == Subtask.State.SUCCESS)
	    results.add(subtask.get());
    }

    @Override
    public MyScope<T> join() throws InterruptedException {
	super.join();
	return this;
    }

    // Returns a stream of results from the subtasks that completed successfully
    public Stream<T> results() {
	super.ensureOwnerAndJoined();
	return results.stream();
    }
}
