package com.github.pointbre.fluxer.core;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;

import com.github.pointbre.fluxer.util.TcpUtil;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import reactor.test.StepVerifier;

public class TcpServerFluxerStepDefinitions {
	private Integer portNumber;
	Fluxer fluxer;

	@Given("a free port is found")
	public void a_free_port_is_found() {
		portNumber = TcpUtil.findFreePort();
	}
	
	@Given("TCP server is created on {string}")
	public void tcp_server_is_created_on(String host) {
		fluxer = new TcpServerFluxer(host, portNumber);
	}	

	@When("TCP server starts")
	public void tcp_server_starts() {
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
	}

	@When("TCP server stops")
	public void tcp_server_stops() {
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
	}

	@Then("status changes: stopped -> starting -> started -> stopping -> stopped")
	public void status_changes_stopped_starting_started_stopping_stopped() {
		StepVerifier.create(fluxer.status())
		.expectNext(Fluxer.Status.STOPPED)
		.expectNext(Fluxer.Status.STARTING)
		.expectNext(Fluxer.Status.STARTED)
		.expectNext(Fluxer.Status.STOPPING)
		.expectNext(Fluxer.Status.STOPPED)
		.expectComplete();
	}
}
