package com.github.pointbre.fluxer.core.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.github.pointbre.fluxer.core.Fluxer;
import com.github.pointbre.fluxer.core.TcpClientFluxer;
import com.github.pointbre.fluxer.core.TcpFluxer;
import com.github.pointbre.fluxer.core.AbstractTcpFluxer;
import com.github.pointbre.fluxer.core.TcpServerFluxer;
import com.github.pointbre.fluxer.util.TcpUtil;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

public class TcpClientTestStepDefinitions {
	private static final int WAIT_TIMEOUT = 10;
	private static final String EMPTY_STRING = "";

	Integer portNumberToUse;

	AbstractTcpFluxer tcpServerFluxer1;
	Disposable tcpServerFluxer1Status;
	Disposable tcpServerFluxer1Link;
	Disposable tcpServerFluxer1Read;
	List<Fluxer.State> tcpServerFluxer1StatusList = new ArrayList<>();
	List<Fluxer.Link> tcpServerFluxer1LinkList = new ArrayList<>();
	List<Fluxer.Message> tcpServerFluxer1ReadList = new ArrayList<>();

	AbstractTcpFluxer tcpClientFluxer1;
	Disposable tcpClientFluxer1Status;
	Disposable tcpClientFluxer1Link;
	Disposable tcpClientFluxer1Read;
	List<Fluxer.State> tcpClientFluxer1StatusList = new ArrayList<>();
	List<Fluxer.Link> tcpClientFluxer1LinkList = new ArrayList<>();
	List<Fluxer.Message> tcpClientFluxer1ReadList = new ArrayList<>();

	// Before the first step of each scenario
	@Before
	public void setup() {
	}

	// After the last step of each scenario
	@After
	public void tearDown(Scenario scenario) {

		if (tcpServerFluxer1Status != null) {
			tcpServerFluxer1Status.dispose();
		}
		if (tcpServerFluxer1Link != null) {
			tcpServerFluxer1Link.dispose();
		}
		if (tcpServerFluxer1Read != null) {
			tcpServerFluxer1Read.dispose();
		}
		if (tcpServerFluxer1 != null) {
			final CountDownLatch countDownLatch1 = new CountDownLatch(1);
			tcpServerFluxer1.stop().doOnError(ex -> {
				countDownLatch1.countDown();
			}).doOnSuccess(__ -> {
				countDownLatch1.countDown();
			}).subscribe();
			try {
				countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {

			}
		}
		tcpServerFluxer1StatusList.clear();
		tcpServerFluxer1LinkList.clear();
		tcpServerFluxer1ReadList.clear();

		if (tcpClientFluxer1Status != null) {
			tcpClientFluxer1Status.dispose();
		}
		if (tcpClientFluxer1Link != null) {
			tcpClientFluxer1Link.dispose();
		}
		if (tcpClientFluxer1Read != null) {
			tcpClientFluxer1Read.dispose();
		}
		if (tcpClientFluxer1 != null) {
			final CountDownLatch countDownLatch1 = new CountDownLatch(1);
			tcpClientFluxer1.stop().doOnError(ex -> {
				countDownLatch1.countDown();
			}).doOnSuccess(__ -> {
				countDownLatch1.countDown();
			}).subscribe();
			try {
				countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {

			}
		}
		tcpClientFluxer1StatusList.clear();
		tcpClientFluxer1LinkList.clear();
		tcpClientFluxer1ReadList.clear();
	}

	@Given("a free port number is found")
	public void a_free_port_number_is_found() {
		try {
			portNumberToUse = TcpUtil.findFreePort();
		} catch (Exception e) {
			fail("Failed to find a free port number");
		}
	}

	@When("{string} is created for an empty string host at the found free port number")
	public void fluxer_is_created_for_an_empty_string_host_at_the_found_free_port_number(String fluxer) throws Exception {
		if ("TCP client 1".equals(fluxer)) {
			tcpClientFluxer1 = new TcpClientFluxer(EMPTY_STRING, TcpUtil.findFreePort());
		} else if ("TCP server 1".equals(fluxer)) {
			tcpServerFluxer1 = new TcpServerFluxer(EMPTY_STRING, TcpUtil.findFreePort());
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
	}

	@When("{string} is created for null host at the found free port number")
	public void fluxer_is_created_for_null_host_at_the_found_free_port_number(String fluxer) throws Exception {
		if ("TCP client 1".equals(fluxer)) {
			tcpClientFluxer1 = new TcpClientFluxer(null, TcpUtil.findFreePort());
		} else if ("TCP server 1".equals(fluxer)) {
			tcpServerFluxer1 = new TcpServerFluxer(null, TcpUtil.findFreePort());
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
	}

	@When("{string} is created for {string} at the port number {int}")
	public void fluxer_is_created_for_the_given_host_at_the_given_port_number(String fluxer, String host,
			int portNumber) throws Exception {
		if ("TCP client 1".equals(fluxer)) {
			tcpClientFluxer1 = new TcpClientFluxer(host, portNumber);
		} else if ("TCP server 1".equals(fluxer)) {
			tcpServerFluxer1 = new TcpServerFluxer(host, portNumber);
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
	}

	@When("{string} is created for {string} at the found free port number")
	public void fluxer_is_created_at_the_found_free_port_number(String fluxer, String host) throws Exception {
		if ("TCP client 1".equals(fluxer)) {
			tcpClientFluxer1 = new TcpClientFluxer(host, TcpUtil.findFreePort());
		} else if ("TCP server 1".equals(fluxer)) {
			tcpServerFluxer1 = new TcpServerFluxer(host, TcpUtil.findFreePort());
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
	}

	@When("{string} is created for {string} at the given port number")
	public void fluxer_is_created_at_the_given_port_number(String fluxer, String host) throws Exception {
		if ("TCP client 1".equals(fluxer)) {
			tcpClientFluxer1 = new TcpClientFluxer(host, portNumberToUse);
		} else if ("TCP server 1".equals(fluxer)) {
			tcpServerFluxer1 = new TcpServerFluxer(host, portNumberToUse);
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
	}

	@When("{string} starts successfully")
	public void fluxer_starts_successfully(String fluxer) {
		AbstractTcpFluxer fluxerToTest = null;
		if ("TCP client 1".equals(fluxer)) {
			fluxerToTest = tcpClientFluxer1;
		} else if ("TCP server 1".equals(fluxer)) {
			fluxerToTest = tcpServerFluxer1;
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
		StepVerifier.create(fluxerToTest.start()).verifyComplete();
	}

	@When("a new subscriber registers to status of {string}")
	public void a_new_subscription_to_fluxer_status(String fluxer) {
		AbstractTcpFluxer fluxerToTest = null;
		if ("TCP client 1".equals(fluxer)) {
			fluxerToTest = tcpClientFluxer1;
			tcpClientFluxer1StatusList.clear();
		} else if ("TCP server 1".equals(fluxer)) {
			fluxerToTest = tcpServerFluxer1;
			tcpServerFluxer1StatusList.clear();
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
		fluxerToTest.state().doOnError(error -> {
			fail("Shouldn't throw an error");
		}).doOnNext(status -> {
			if ("TCP client 1".equals(fluxer)) {
				tcpClientFluxer1StatusList.add(status);
			} else if ("TCP server 1".equals(fluxer)) {
				tcpServerFluxer1StatusList.add(status);
			}
		}).subscribe();
	}

	@When("{string} stops successfully")
	public void fluxer_stops_successfully(String fluxer) {
		AbstractTcpFluxer fluxerToTest = null;
		if ("TCP client 1".equals(fluxer)) {
			fluxerToTest = tcpClientFluxer1;
		} else if ("TCP server 1".equals(fluxer)) {
			fluxerToTest = tcpServerFluxer1;
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
		StepVerifier.create(fluxerToTest.stop()).expectComplete().verify();
		System.out.println(fluxer + ">>> 3 Received status: " + tcpClientFluxer1StatusList.toString());
		assertTrue(tcpClientFluxer1StatusList.size() > 0);
	}

	@Then("{string} cannot start")
	public void fluxer_cannot_start(String fluxer) {
		TcpFluxer fluxerToTest = null;
		if ("TCP client 1".equals(fluxer)) {
			fluxerToTest = tcpClientFluxer1;
		} else if ("TCP server 1".equals(fluxer)) {
			fluxerToTest = tcpServerFluxer1;
		} else {
			fail("Please check the fluxer name of when description: " + fluxer);
		}
//		StepVerifier.create(fluxerToTest.start()).expectError(StartException.class).verify();
	}

//	@Then("the subscriber of status of {string} receives: STOPPED -> STARTING -> STARTED -> STOPPING -> STOPPED")
//	public void fluxer_xxxx(String fluxer) {
//		List<Fluxer.State> statusListToCheck = null;
//		if ("TCP client 1".equals(fluxer)) {
//			statusListToCheck = tcpClientFluxer1StatusList;
//		} else if ("TCP server 1".equals(fluxer)) {
//			statusListToCheck = tcpServerFluxer1StatusList;
//			System.out.println(fluxer +":" + tcpServerFluxer1StatusList.toString());
//		} else {
//			fail("Please check the fluxer name of when description: " + fluxer);
//		}
//		
//		assertEquals(5, statusListToCheck.size());
//		assertEquals(Fluxer.State.STOPPED, statusListToCheck.get(0));
//		assertEquals(Fluxer.State.STARTING, statusListToCheck.get(1));
//		assertEquals(Fluxer.State.STARTED, statusListToCheck.get(2));
//		assertEquals(Fluxer.State.STOPPING, statusListToCheck.get(3));
//		assertEquals(Fluxer.State.STOPPED, statusListToCheck.get(4));
//	}

//	
//	@Then("TCP client 1 cannot start")
//	public void tcp_client_1_cannot_start() {
//		final CountDownLatch countDownLatch = new CountDownLatch(1);
//
//		tcpClientFluxer1.start().doOnError(ex -> {
//			countDownLatch.countDown();
//			assertTrue(ex instanceof StartException);
//		}).doOnSuccess(__ -> {
//			fail("TCP client 1 start() should fail");
//			countDownLatch.countDown();
//		}).subscribe();
//		try {
//			countDownLatch.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP client 1 start() timed out");
//		}
//	}

//	@Given("TCP client 1 is created at the found free port 1")
//	public void tcp_client_is_created_on_at_the_found_free_port() {
//		tcpClientFluxer1 = new TcpClientFluxer(HOST_TO_TEST, portNumber1);
//	}
//
//	@Given("TCP client 1 is created at {int}")
//	public void tcp_client_1_is_created_on_at(int portNumber) {
//		tcpClientFluxer1 = new TcpClientFluxer(HOST_TO_TEST, portNumber);
//	}
//
//	@When("TCP server 1 starts successfully")
//	public void tcp_server_1_starts_successfully() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpServerFluxer1.start().doOnError(ex -> {
//			fail("TCP server 1 start() failed: " + ex.getMessage());
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP server 1 start() timed out");
//		}
//	}
//
//	@When("TCP server 1 stops successfully")
//	public void tcp_server_1_stops_successfully() {
//		final CountDownLatch countDownLatch2 = new CountDownLatch(1);
//
//		tcpServerFluxer1.stop().doOnError(ex -> {
//			fail("TCP server 1 stop() failed: " + ex.getMessage());
//			countDownLatch2.countDown();
//		}).doOnSuccess(__ -> {
//			countDownLatch2.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch2.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP server 1 stop() timed out");
//		}
//	}
//
//	@When("TCP client 1 starts successfully")
//	public void tcp_client_1_starts_successfully() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpClientFluxer1.start().doOnError(ex -> {
//			fail("TCP client 1 start() failed: " + ex.getMessage());
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP client 1 start() timed out");
//		}
//	}
//
//	@When("TCP client 1 stops successfully")
//	public void tcp_client_1_stops_successfully() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpClientFluxer1.stop().doOnError(ex -> {
//			fail("TCP client 1 stop() failed: " + ex.getMessage());
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP client 1 stop() timed out");
//		}
//	}
//
//	@When("TCP client 1 writes a binary message {string} to the TCP server 1")
//	public void tcp_client_1_writes_a_binary_message_to_the_tcp_server_1(String messageToSend) {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		Link targetLink = new Link(new Endpoint(HOST_TO_TEST, -1), new Endpoint(HOST_TO_TEST, portNumber1),
//				Link.Status.NONE);
//
//		tcpClientFluxer1.write(new Message(targetLink, ByteBufUtil.decodeHexDump(messageToSend))).doOnError(ex -> {
//			fail("TCP client 1 write() failed: " + ex.getMessage());
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP client 1 write() timed out");
//		}
//
////		// This sleep is to make sure the message is sent separately without being
////		// merged to the previous message
////		try {
////			Thread.sleep(500);
////		} catch (InterruptedException e) {
////			fail("TCP client 1 write() timed out");
////		}
//	}
//
//	@When("TCP server 1 writes a binary message {string} to the TCP client 1")
//	public void tcp_server_writes_a_binary_message_to_the_tcp_client(String messageToSend) {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpServerFluxer1.write(new Message(connectedClientLink, ByteBufUtil.decodeHexDump(messageToSend)))
//				.doOnError(ex -> {
//					fail("TCP server 1 write() failed: " + ex.getMessage());
//					countDownLatch1.countDown();
//				}).doOnSuccess(__ -> {
//					countDownLatch1.countDown();
//				}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP server 1 write() timed out");
//		}
//
//		// This sleep is to make sure the message is sent separately without being
//		// merged to the previous message
//		try {
//			Thread.sleep(500);
//		} catch (InterruptedException e) {
//			fail("TCP server 1 write() timed out");
//		}
//	}
//
//	@When("TCP server 1 publishes its link changes")
//	public void tcp_server_1_publishes_its_link_changes() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpServerFluxer1Link = tcpServerFluxer1.link().doOnError(ex -> {
//			fail("TCP server 1 link() failed: " + ex.getMessage());
//			countDownLatch1.countDown();
//		}).doOnNext(link -> {
//			connectedClientLink = link;
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP server 1 link() timed out");
//		}
//
//		tcpServerFluxer1Link.dispose();
//	}
//
//	@Then("TCP server 1 cannot start")
//	public void tcp_server_1_cannot_start() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpServerFluxer1.start().doOnError(ex -> {
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			fail("TCP server 1 start() should fail");
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP server 1 start() timed out");
//		}
//	}
//
//	@Then("TCP server 2 cannot start")
//	public void tcp_server_2_cannot_start() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpServerFluxer2.start().doOnError(ex -> {
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			fail("TCP server 2 start() should fail");
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP server 2 start() timed out");
//		}
//	}
//
//	@Then("TCP client 1 cannot start")
//	public void tcp_client_1_cannot_start() {
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//
//		tcpClientFluxer1.start().doOnError(ex -> {
//			countDownLatch1.countDown();
//		}).doOnSuccess(__ -> {
//			fail("TCP client 1 start() should fail");
//			countDownLatch1.countDown();
//		}).subscribe();
//
//		try {
//			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("TCP client 1 start() timed out");
//		}
//	}
//
//	@Then("TCP server 1 publishes its status changes: stopped -> starting -> started -> stopping -> stopped")
//	public void tcp_server_1_status_changes_stopped_starting_started_stopping_stopped() {
//		StepVerifier.create(tcpServerFluxer1.status()).expectNext(Fluxer.Status.STOPPED)
//				.expectNext(Fluxer.Status.STARTING).expectNext(Fluxer.Status.STARTED).expectNext(Fluxer.Status.STOPPING)
//				.expectNext(Fluxer.Status.STOPPED).expectComplete().verify();
//	}
//
//	@When("TCP server 1 publishes its link changes: connected -> disconnected")
//	public void tcp_server_1_publishes_its_link_changes_connected_disconnected() {
//		// Please note that the port allocated to the connected client is random, so
//		// verifying only it's not same with server's port
//		StepVerifier.create(tcpServerFluxer1.link()).expectNextMatches(link -> {
//			return link.getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& link.getLocalEndpoint().getPort().equals(portNumber1)
//					&& link.getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& !link.getRemoteEndpoint().getPort().equals(portNumber1)
//					&& link.getStatus().equals(Link.Status.CONNECTED);
//		})
////				.thenAwait()
//				.expectNextMatches(link -> {
//					return link.getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST)
//							&& link.getLocalEndpoint().getPort().equals(portNumber1)
//							&& link.getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST)
//							&& !link.getRemoteEndpoint().getPort().equals(portNumber1)
//							&& link.getStatus().equals(Link.Status.DISCONNECTED);
//				})
////				.thenAwait()
//				.expectComplete().verify();
//	}
//
//	@Then("TCP server 1 publishes its read changes: 2 binary messages {string} and {string}")
//	public void tcp_server_publishes_its_read_changes_2_binary_messages(String expectedMessage1,
//			String expectedMessage2) {
//		StepVerifier.create(tcpServerFluxer1.read()).expectNextMatches(message -> {
//			return ByteBufUtil.hexDump(message.getMessage()).equals(expectedMessage1)
//					&& message.getLink().getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& message.getLink().getLocalEndpoint().getPort().equals(portNumber1)
//					&& message.getLink().getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& !message.getLink().getRemoteEndpoint().getPort().equals(portNumber1)
//					&& message.getLink().getStatus().equals(Link.Status.CONNECTED);
//		}).expectNextMatches(message -> {
//			return ByteBufUtil.hexDump(message.getMessage()).equals(expectedMessage2)
//					&& message.getLink().getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& message.getLink().getLocalEndpoint().getPort().equals(portNumber1)
//					&& message.getLink().getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& !message.getLink().getRemoteEndpoint().getPort().equals(portNumber1)
//					&& message.getLink().getStatus().equals(Link.Status.CONNECTED);
//		}).expectComplete().verify();
//	}
//
//	@Then("TCP server 1 publishes its read changes: 1 binary message {string}")
//	public void tcp_server_publishes_its_read_changes_1_binary_message(String expectedMessage1) {
//		StepVerifier.create(tcpServerFluxer1.read()).expectNextMatches(message -> {
//			return ByteBufUtil.hexDump(message.getMessage()).equals(expectedMessage1)
//					&& message.getLink().getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& message.getLink().getLocalEndpoint().getPort().equals(portNumber1)
//					&& message.getLink().getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST)
//					&& !message.getLink().getRemoteEndpoint().getPort().equals(portNumber1)
//					&& message.getLink().getStatus().equals(Link.Status.CONNECTED);
//		}).expectComplete().verify();
//	}
}
