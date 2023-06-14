package com.github.pointbre.fluxer.core.bdd;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.github.pointbre.fluxer.core.Fluxer;
import com.github.pointbre.fluxer.core.Fluxer.Endpoint;
import com.github.pointbre.fluxer.core.Fluxer.Link;
import com.github.pointbre.fluxer.core.Fluxer.Message;
import com.github.pointbre.fluxer.core.TcpClientFluxer;
import com.github.pointbre.fluxer.core.TcpFluxer;
import com.github.pointbre.fluxer.core.TcpServerFluxer;
import com.github.pointbre.fluxer.util.TcpUtil;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.netty.buffer.ByteBufUtil;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

public class TcpFluxerStepDefinitions {
	private static final int WAIT_TIMEOUT = 30;
	private static final String HOST_TO_TEST = "127.0.0.1";
	private static final String EMPTY_STRING = "";

	private Integer portNumber1;

	TcpFluxer tcpServerFluxer1;
	TcpFluxer tcpServerFluxer2;

	Disposable tcpServerFluxer1Status;
	Disposable tcpServerFluxer1Link;
	Disposable tcpServerFluxer1Read;

	Disposable tcpServerFluxer2Status;
	Disposable tcpServerFluxer2Link;
	Disposable tcpServerFluxer2Read;

	TcpFluxer tcpClientFluxer1;
	Link connectedClientLink;

	Disposable tcpClientFluxer1Status;
	Disposable tcpClientFluxer1Link;
	Disposable tcpClientFluxer1Read;

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
			tcpServerFluxer1.stop()
					.doOnError(ex -> {
						countDownLatch1.countDown();
					})
					.doOnSuccess(__ -> {
						countDownLatch1.countDown();
					})
					.subscribe();

			try {
				countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {

			}
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
		if (tcpServerFluxer2 != null) {
			final CountDownLatch countDownLatch1 = new CountDownLatch(1);
			tcpServerFluxer2.stop()
					.doOnError(ex -> {
						countDownLatch1.countDown();
					})
					.doOnSuccess(__ -> {
						countDownLatch1.countDown();
					})
					.subscribe();

			try {
				countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {

			}
		}

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
			tcpClientFluxer1.stop()
					.doOnError(ex -> {
						countDownLatch1.countDown();
					})
					.doOnSuccess(__ -> {
						countDownLatch1.countDown();
					})
					.subscribe();

			try {
				countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {

			}
		}
	}

	@Given("a free port 1 is found")
	public void a_free_port_1_is_found() {
		portNumber1 = TcpUtil.findFreePort();
	}

	@Given("TCP server 1 is created on an empty string at the found free port 1")
	public void tcp_server_1_is_created_on_an_empty_string_at_the_found_free_port_1() {
		tcpServerFluxer1 = new TcpServerFluxer(EMPTY_STRING, portNumber1);
	}

	@Given("TCP client 1 is created on an empty string at the found free port 1")
	public void tcp_client_is_created_on_an_empty_string_at_the_found_free_port_1() {
		tcpClientFluxer1 = new TcpClientFluxer(EMPTY_STRING, portNumber1);
	}

	@Given("TCP server 1 is created at the found free port 1")
	public void tcp_server_1_is_created_on_at_the_found_free_port_1() {
		tcpServerFluxer1 = new TcpServerFluxer(HOST_TO_TEST, portNumber1);
	}

	@Given("TCP server 1 is created at {int}")
	public void tcp_server_1_is_created_on_at(int portNumber) {
		tcpServerFluxer1 = new TcpServerFluxer(HOST_TO_TEST, portNumber);
	}

	@Given("TCP server 2 is created at the found free port 1")
	public void tcp_server_2_is_created_on_at_the_found_free_port_1() {
		tcpServerFluxer2 = new TcpServerFluxer(HOST_TO_TEST, portNumber1);
	}

	@Given("TCP client 1 is created at the found free port 1")
	public void tcp_client_is_created_on_at_the_found_free_port() {
		tcpClientFluxer1 = new TcpClientFluxer(HOST_TO_TEST, portNumber1);
	}

	@Given("TCP client 1 is created at {int}")
	public void tcp_client_1_is_created_on_at(int portNumber) {
		tcpClientFluxer1 = new TcpClientFluxer(HOST_TO_TEST, portNumber);
	}

	@When("TCP server 1 starts successfully")
	public void tcp_server_1_starts_successfully() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer1.start()
				.doOnError(ex -> {
					fail("TCP server 1 start() failed: " + ex.getMessage());
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 start() timed out");
		}
	}

	@When("TCP server 1 stops successfully")
	public void tcp_server_1_stops_successfully() {
		final CountDownLatch countDownLatch2 = new CountDownLatch(1);

		tcpServerFluxer1.stop()
				.doOnError(ex -> {
					fail("TCP server 1 stop() failed: " + ex.getMessage());
					countDownLatch2.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch2.countDown();
				})
				.subscribe();

		try {
			countDownLatch2.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 stop() timed out");
		}
	}

	@When("TCP client 1 starts successfully")
	public void tcp_client_1_starts_successfully() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpClientFluxer1.start()
				.doOnError(ex -> {
					fail("TCP client 1 start() failed: " + ex.getMessage());
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP client 1 start() timed out");
		}
	}

	@When("TCP client 1 stops successfully")
	public void tcp_client_1_stops_successfully() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpClientFluxer1.stop()
				.doOnError(ex -> {
					fail("TCP client 1 stop() failed: " + ex.getMessage());
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP client 1 stop() timed out");
		}
	}

	@When("TCP client 1 writes a binary message {string} to the TCP server 1")
	public void tcp_client_1_writes_a_binary_message_to_the_tcp_server_1(String messageToSend) {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		Link targetLink = new Link(new Endpoint(HOST_TO_TEST, -1), new Endpoint(HOST_TO_TEST, portNumber1),
				Link.Status.NONE);

		tcpClientFluxer1.write(new Message(targetLink, ByteBufUtil.decodeHexDump(messageToSend)))
				.doOnError(ex -> {
					fail("TCP client 1 write() failed: " + ex.getMessage());
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP client 1 write() timed out");
		}

		// This sleep is to make sure the message is sent separately without being
		// merged to the previous message
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			fail("TCP client 1 write() timed out");
		}
	}

	@When("TCP server 1 writes a binary message {string} to the TCP client 1")
	public void tcp_server_writes_a_binary_message_to_the_tcp_client(String messageToSend) {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer1.write(new Message(connectedClientLink, ByteBufUtil.decodeHexDump(messageToSend)))
				.doOnError(ex -> {
					fail("TCP server 1 write() failed: " + ex.getMessage());
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 write() timed out");
		}

		// This sleep is to make sure the message is sent separately without being
		// merged to the previous message
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			fail("TCP server 1 write() timed out");
		}
	}

	@When("TCP server 1 publishes its link changes")
	public void tcp_server_1_publishes_its_link_changes() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer1Link = tcpServerFluxer1.link()
				.doOnError(ex -> {
					fail("TCP server 1 link() failed: " + ex.getMessage());
				})
				.doOnNext(link -> {
					connectedClientLink = link;
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 link() timed out");
		}
		
		tcpServerFluxer1Link.dispose();
	}

	@Then("TCP server 1 cannot start")
	public void tcp_server_1_cannot_start() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer1.start()
				.doOnError(ex -> {
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					fail("TCP server 1 start() should fail");
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 1 start() timed out");
		}
	}

	@Then("TCP server 2 cannot start")
	public void tcp_server_2_cannot_start() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpServerFluxer2.start()
				.doOnError(ex -> {
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					fail("TCP server 2 start() should fail");
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP server 2 start() timed out");
		}
	}

	@Then("TCP client 1 cannot start")
	public void tcp_client_1_cannot_start() {
		final CountDownLatch countDownLatch1 = new CountDownLatch(1);

		tcpClientFluxer1.start()
				.doOnError(ex -> {
					countDownLatch1.countDown();
				})
				.doOnSuccess(__ -> {
					fail("TCP client 1 start() should fail");
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			countDownLatch1.await(WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("TCP client 1 start() timed out");
		}
	}

	@Then("TCP server 1 publishes its status changes: stopped -> starting -> started -> stopping -> stopped")
	public void tcp_server_1_status_changes_stopped_starting_started_stopping_stopped() {
		StepVerifier.create(tcpServerFluxer1.status())
				.expectNext(Fluxer.Status.STOPPED)
				.expectNext(Fluxer.Status.STARTING)
				.expectNext(Fluxer.Status.STARTED)
				.expectNext(Fluxer.Status.STOPPING)
				.expectNext(Fluxer.Status.STOPPED)
				.expectComplete()
				.verify();
	}

	@Then("TCP server 1 publishes its link changes: connected -> disconnected")
	public void tcp_server_1_publishes_its_link_changes_connected_disconnected() {
		// Please note that the port allocated to the connected client is random, so
		// verifying only it's not same with server's port
		StepVerifier.create(tcpServerFluxer1.link())
				.expectNextMatches(link -> {
					return link.getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							link.getLocalEndpoint().getPort().equals(portNumber1) &&
							link.getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							!link.getRemoteEndpoint().getPort().equals(portNumber1) &&
							link.getStatus().equals(Link.Status.CONNECTED);
				})
				.expectNextMatches(link -> {
					return link.getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							link.getLocalEndpoint().getPort().equals(portNumber1) &&
							link.getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							!link.getRemoteEndpoint().getPort().equals(portNumber1) &&
							link.getStatus().equals(Link.Status.DISCONNECTED);
				})
				.expectComplete()
				.verify();
	}

	@Then("TCP server 1 publishes its read changes: 2 binary messages {string} and {string}")
	public void tcp_server_publishes_its_read_changes_a_binary_message(String expectedMessage1,
			String expectedMessage2) {
		StepVerifier.create(tcpServerFluxer1.read())
				.expectNextMatches(message -> {
					return ByteBufUtil.hexDump(message.getMessage()).equals(expectedMessage1) &&
							message.getLink().getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							message.getLink().getLocalEndpoint().getPort().equals(portNumber1) &&
							message.getLink().getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							!message.getLink().getRemoteEndpoint().getPort().equals(portNumber1) &&
							message.getLink().getStatus().equals(Link.Status.CONNECTED);
				})
				.expectNextMatches(message -> {
					return ByteBufUtil.hexDump(message.getMessage()).equals(expectedMessage2) &&
							message.getLink().getLocalEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							message.getLink().getLocalEndpoint().getPort().equals(portNumber1) &&
							message.getLink().getRemoteEndpoint().getIpAddress().equals(HOST_TO_TEST) &&
							!message.getLink().getRemoteEndpoint().getPort().equals(portNumber1) &&
							message.getLink().getStatus().equals(Link.Status.CONNECTED);
				})
				.expectComplete()
				.verify();
	}
}
