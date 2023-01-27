package com.github.pointbre.fluxer.core;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@ExtendWith(Slf4jExtension.class)
@ExtendWith(PortNumberExtension.class)
class TcpServerFluxerTest {

	private Logger logger;
	private Integer portNumber;


	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	public void setPort(Integer portNumber) {
		this.portNumber = portNumber;
	}

	@Test
	void test() {

		Fluxer fluxer = new TcpServerFluxer("localhost", portNumber);

		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		fluxer.start()
				.doOnError(ex -> {
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await();
		} catch (InterruptedException e) {
			fail("Server failed to start");
		}

		final CountDownLatch countDownLatch2 = new CountDownLatch(1);

		fluxer.stop()
				.doOnError(ex -> {
				})
				.doOnSuccess(__ -> {
					countDownLatch2.countDown();
				})
				.subscribe();

		try {
			countDownLatch2.await();
		} catch (InterruptedException e) {
			fail("Server stop to start");
		}

		StepVerifier.create(fluxer.status())
				.expectNext(Fluxer.Status.STOPPED)
				.expectNext(Fluxer.Status.STARTING)
				.expectNext(Fluxer.Status.STARTED)
				.expectNext(Fluxer.Status.STOPPING)
				.expectNext(Fluxer.Status.STOPPED)
				.expectComplete();

	}
}
