package com.github.pointbre.fluxer.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.event.Level;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

public class ReactorAndVirtualThreadDemo {

    public static void main(String[] args) {

	Many<String> testSink = Sinks
		.many()
		.multicast().<String>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
	
	

	Flux<String> testFlux = testSink.asFlux()
		.doOnNext(s -> {
		    System.out.println("Flux publisher: pushed " + s);
		    System.out.println("Flux publisher: running in virtual thread? " + Thread.currentThread().isVirtual() + ", " + Thread.currentThread().getName());
		})
		.publishOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
		.log();

	CountDownLatch latch = new CountDownLatch(1);
	
	testFlux
	.subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
	.subscribe(s -> {
	    System.out.println("Flux subscriber: received " + s);
	    System.out.println("Flux subscriber: running in virtual thread? " + Thread.currentThread().isVirtual() + ", " + Thread.currentThread().getName());
	    latch.countDown();
	});
	
	System.out.println("Before or after?");
	
	testSink.tryEmitNext("test");
	
	try {
	    latch.await();
	} catch (InterruptedException e) {
	}
	
    }
}
