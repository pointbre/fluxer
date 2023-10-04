package com.github.pointbre.fluxer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import com.github.pointbre.fluxer.core.Fluxer.Link.Status;
import com.github.pointbre.fluxer.core.Fluxer.Endpoint;
import com.github.pointbre.fluxer.core.Fluxer.Link;
import com.github.pointbre.fluxer.core.Fluxer.Message;

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

		Fluxer tcpServer = new TcpServerFluxer("127.0.0.1", portNumber);
		List<Fluxer.Status> serverStatus = new ArrayList<>();
		List<Fluxer.Link> serverLink = new ArrayList<>();
		List<Fluxer.Message> serverInbound = new ArrayList<>();
		Fluxer tcpClient = new TcpClientFluxer("127.0.0.1", portNumber);
		List<Fluxer.Status> clientStatus = new ArrayList<>();
		List<Fluxer.Link> clientLink = new ArrayList<>();
		List<Fluxer.Message> clientInbound = new ArrayList<>();

		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
		tcpServer.start().doOnError(err -> {
			countDownLatch1.countDown();
			System.out.println("Server failed to start at " + portNumber);
		}).doOnSuccess(__ -> {
			countDownLatch1.countDown();
			logger.debug("Server started at " + portNumber);
		}).subscribe();
		try {
			countDownLatch1.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("Server failed to start");
		}

		final CountDownLatch countDownLatch2 = new CountDownLatch(1);
		tcpClient.start().doOnError(rr -> {
			countDownLatch2.countDown();
			System.out.println("Client failed to start at " + portNumber);
		}).doOnSuccess(__ -> {
			countDownLatch2.countDown();
			System.out.println("Client started");
		}).subscribe();
		try {
			countDownLatch2.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("Client failed to start");
		}

		tcpServer.link().doOnError(error -> {
			fail("Shouldn't throw an error");
		}).doOnNext(link -> {
			System.out.println("Server link updated: " + link);
			serverLink.add(link);
		}).doOnComplete(new Runnable() {
			@Override
			public void run() {
				System.out.println("Server link completed and total " + serverLink.size() + " received");
			}
		}).subscribe();

		tcpClient.link().doOnError(error -> {
			fail("Shouldn't throw an error");
		}).doOnNext(link -> {
			System.out.println("Client link updated: " + link);
			clientLink.add(link);
		}).doOnComplete(new Runnable() {
			@Override
			public void run() {
				System.out.println("Client link completed and total " + clientLink.size() + " received");
			}
		}).subscribe();

		tcpClient.status().doOnError(error -> {
			fail("Shouldn't throw an error");
		}).doOnNext(status -> {
			System.out.println("Client status updated: " + status);
			clientStatus.add(status);
		}).doOnComplete(new Runnable() {
			@Override
			public void run() {
				System.out.println("Client status completed and total " + clientStatus.size() + " received");
			}
		}).subscribe();

		tcpClient.read().doOnError(error -> {
			fail("Shouldn't throw an error");
		}).doOnNext(message -> {
			System.out.println("Client message received: " + message);
			clientInbound.add(message);
		}).doOnComplete(new Runnable() {
			@Override
			public void run() {
				System.out.println("Client message completed and total " + clientInbound.size() + " received");
			}
		}).subscribe();

		final CountDownLatch countDownLatch3 = new CountDownLatch(1);
		
	    int leftLimit = 48; // numeral '0'
	    int rightLimit = 122; // letter 'z'
	    int targetStringLength = 1000;
	    Random random = new Random();

	    String generatedString = random.ints(leftLimit, rightLimit + 1)
	      .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
	      .limit(targetStringLength)
	      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
	      .toString();
		
		tcpServer.write(new Message(serverLink.get(0), generatedString.getBytes())).doOnError(error -> {
			countDownLatch3.countDown();
			System.out.println("Writing to server failed");
		}).doOnSuccess(__ -> {
			countDownLatch3.countDown();
			System.out.println("Writing to server done");
		}).subscribe();
		try {
			countDownLatch3.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("Writing to server failed");
		}
		
//		try {
//			Thread.sleep(1L);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
		
		final CountDownLatch countDownLatch4 = new CountDownLatch(1);
		tcpServer.write(new Message(serverLink.get(0), generatedString.getBytes())).doOnError(error -> {
			countDownLatch4.countDown();
			System.out.println("Writing to server failed");
		}).doOnSuccess(__ -> {
			countDownLatch4.countDown();
			System.out.println("Writing to server done");
		}).subscribe();
		try {
			countDownLatch4.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("Writing to server failed");
		}

		final CountDownLatch countDownLatch5 = new CountDownLatch(1);
		tcpClient.stop().doOnError(ex -> {
			System.out.println("Client failed to stop");
		}).doOnSuccess(__ -> {
			countDownLatch5.countDown();
			System.out.println("Client stopped");
		}).subscribe();
		try {
			countDownLatch5.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("Client failed to stop");
		}

		final CountDownLatch countDownLatch6 = new CountDownLatch(1);
		tcpServer.stop().doOnError(ex -> {
			System.out.println("Server failed to stopp");
		}).doOnSuccess(__ -> {
			countDownLatch6.countDown();
			System.out.println("Server stopped");
		}).subscribe();
		try {
			countDownLatch6.await(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			fail("Server failed to stop");
		}
	}
}
