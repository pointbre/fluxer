package com.github.pointbre.fluxer.core.bdd;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.github.pointbre.fluxer.core.Fluxer;
import com.github.pointbre.fluxer.core.TcpServerFluxer;
import com.github.pointbre.fluxer.util.TcpUtil;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

public class TcpServerFluxerStepDefinitions {
	private Integer portNumber1;

	Fluxer tcpServerFluxer1;
	Fluxer tcpServerFluxer2;

	Disposable tcpServerFluxer1Start;
	Disposable tcpServerFluxer1Stop;
	Disposable tcpServerFluxer1Write;
	Disposable tcpServerFluxer1Status;
	Disposable tcpServerFluxer1Link;
	Disposable tcpServerFluxer1Read;
	
	Disposable tcpServerFluxer2Start;
	Disposable tcpServerFluxer2Stop;
	Disposable tcpServerFluxer2Write;
	Disposable tcpServerFluxer2Status;
	Disposable tcpServerFluxer2Link;
	Disposable tcpServerFluxer2Read;

	// Before the first step of each scenario
	@Before
	public void setup() {
	}

	// After the last step of each scenario
	@After
	public void tearDown(Scenario scenario) {
		if (tcpServerFluxer1Start != null) {
			tcpServerFluxer1Start.dispose();
		}
		if (tcpServerFluxer1Stop != null) {
			tcpServerFluxer1Stop.dispose();
		}
		if (tcpServerFluxer1Write != null) {
			tcpServerFluxer1Write.dispose();
		}
		if (tcpServerFluxer1Status != null) {
			tcpServerFluxer1Status.dispose();
		}
		if (tcpServerFluxer1Link != null) {
			tcpServerFluxer1Link.dispose();
		}
		if (tcpServerFluxer1Read != null) {
			tcpServerFluxer1Read.dispose();
		}
		
		if (tcpServerFluxer2Start != null) {
			tcpServerFluxer2Start.dispose();
		}
		if (tcpServerFluxer2Stop != null) {
			tcpServerFluxer2Stop.dispose();
		}
		if (tcpServerFluxer2Write != null) {
			tcpServerFluxer2Write.dispose();
		}
		if (tcpServerFluxer2Status != null) {
			tcpServerFluxer2Status.dispose();
		}
		if (tcpServerFluxer2Link != null) {
			tcpServerFluxer2Link.dispose();
		}
		if (tcpServerFluxer2Read != null) {
			tcpServerFluxer2Read.dispose();
		}
	}

	@Given("a free port 1 is found")
	public void a_free_port_1_is_found() {
		portNumber1 = TcpUtil.findFreePort();
	}

	@Given("TCP server 1 is created on {string} at the found free port 1")
	public void tcp_server_1_is_created_on_at_the_found_free_port_1(String host) {
		tcpServerFluxer1 = new TcpServerFluxer(host, portNumber1);
	}
	
	@Given("TCP server 1 is created on {string} at {int}")
	public void tcp_server_1_is_created_on_at(String host, int portNumber) {
		tcpServerFluxer1 = new TcpServerFluxer(host, portNumber);
	}
	
	@Given("TCP server 2 is created on {string} at the found free port 1")
	public void tcp_server_2_is_created_on_at_the_found_free_port_1(String host) {
		tcpServerFluxer2 = new TcpServerFluxer(host, portNumber1);
	}

	@When("TCP server 1 starts successfully")
	public void tcp_server_1_starts_successfully() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer1Start = tcpServerFluxer1.start()
				.doOnError(ex -> {
					fail("TCP server 1 start() failed: " + ex.getMessage());
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 start() timed out");
		}
	}

	@When("TCP server 1 stops successfully")
	public void tcp_server_1_stops_successfully() {
		final CountDownLatch countDownLatch2 = new CountDownLatch(1);

		tcpServerFluxer1Stop = tcpServerFluxer1.stop()
				.doOnError(ex -> {
					fail("TCP server 1 stop() failed: " + ex.getMessage());
					countDownLatch2.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch2.countDown();
				})
				.subscribe();

		try {
			countDownLatch2.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 stop() timed out");
		}
	}

	@Then("TCP server 1's status changes: stopped -> starting -> started -> stopping -> stopped")
	public void tcp_server_1_status_changes_stopped_starting_started_stopping_stopped() {
		StepVerifier.create(tcpServerFluxer1.status())
				.expectNext(Fluxer.Status.STOPPED)
				.expectNext(Fluxer.Status.STARTING)
				.expectNext(Fluxer.Status.STARTED)
				.expectNext(Fluxer.Status.STOPPING)
				.expectNext(Fluxer.Status.STOPPED)
				.expectComplete();
	}
	
	@Then("TCP server 1 can not start")
	public void tcp_server_1_can_not_start() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer1Start = tcpServerFluxer1.start()
				.doOnError(ex -> {
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					fail("TCP server 1 start() should fail");
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 start() timed out");
		}
	}
	
	@Then("TCP server 2 can not start")
	public void tcp_server_2_can_not_start() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer2Start = tcpServerFluxer2.start()
				.doOnError(ex -> {
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					fail("TCP server 2 start() should fail");
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 2 start() timed out");
		}
	}
}
