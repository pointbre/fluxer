package com.github.pointbre.fluxer.core;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.pointbre.fluxer.core.Fluxer.Link;

import lombok.Cleanup;

@ExtendWith(MockitoExtension.class)
@ExtendWith(PortNumberExtension.class)
class TcpServerFluxerTest {

    private static final String LOCALHOST_IP_ADDR = "127.0.0.1";
    private Integer portNumber;

    
    public void setPortNumber(Integer portNumber) {
	this.portNumber = portNumber;
    }

    @Test
    void test() throws Exception {
	@Cleanup
	Fluxer<byte[]> tcpServer = new TcpServerFluxer(LOCALHOST_IP_ADDR, portNumber);

	@Cleanup
	final Fluxer<byte[]> tcpClient = new TcpClientFluxer(LOCALHOST_IP_ADDR, portNumber);

	final List<Link> serverLinks = new ArrayList<>();
	final List<Link> clientLinks = new ArrayList<>();

	// Subscribe to state right after tcp server instance is created
	tcpServer.state()
		.doOnError(error -> {
		    fail("Shouldn't throw an error");
		}).doOnNext(state -> {
		    System.out.println("subscriber 1 | Server state received: " + state);
		}).doOnComplete(new Runnable() {
		    @Override
		    public void run() {
			System.out.println(
				"subscriber 1 | Server state completed");
		    }
		}).subscribe();

	tcpServer.link()
		.doOnError(error -> {
		    fail("Shouldn't throw an error");
		}).doOnNext(link -> {
		    serverLinks.add(link);
		    System.out.println("subscriber 1 | Server link received: " + link + ", currently "
			    + serverLinks.size() + " connected");
		}).doOnComplete(new Runnable() {
		    @Override
		    public void run() {
			System.out.println(
				"subscriber 1 | Server link completed");
		    }
		}).subscribe();

	tcpServer.message()
		.doOnError(error -> {
		    fail("Shouldn't throw an error");
		}).doOnNext(message -> {
		    System.out.println("subscriber 1 | Server message received: " + message);
		}).doOnComplete(new Runnable() {
		    @Override
		    public void run() {
			System.out.println(
				"subscriber 1 | Server message completed");
		    }
		}).subscribe();

	tcpClient.state()
		.doOnError(error -> {
		    fail("Shouldn't throw an error");
		}).doOnNext(state -> {
		    System.out.println("subscriber 1 | client state received: " + state);
		}).doOnComplete(new Runnable() {
		    @Override
		    public void run() {
			System.out.println(
				"subscriber 1 | client state completed");
		    }
		}).subscribe();

	tcpClient.link()
		.doOnError(error -> {
		    fail("Shouldn't throw an error");
		}).doOnNext(link -> {
		    clientLinks.add(link);
		    System.out.println("subscriber 1 | client link received: " + link);
		}).doOnComplete(new Runnable() {
		    @Override
		    public void run() {
			System.out.println(
				"subscriber 1 | client link completed");
		    }
		}).subscribe();

	tcpClient.message()
		.doOnError(error -> {
		    fail("Shouldn't throw an error");
		}).doOnNext(message -> {
		    System.out.println("subscriber 1 | client message received: " + message);
		}).doOnComplete(new Runnable() {
		    @Override
		    public void run() {
			System.out.println(
				"subscriber 1 | client message completed");
		    }
		}).subscribe();

	long now1 = System.currentTimeMillis();
	final CountDownLatch countDownLatch1 = new CountDownLatch(1);
	tcpServer.start().doOnError(err -> {
	    countDownLatch1.countDown();
	    System.out.println(System.currentTimeMillis() + ":Server failed to start at " + portNumber);
	}).doOnSuccess(result -> {
	    countDownLatch1.countDown();
	    System.out.println(System.currentTimeMillis() + ":" + result);
	    System.out.println("Server starting took " + (System.currentTimeMillis() - now1));
	}).subscribe();
	try {
	    countDownLatch1.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    fail("Server failed to start");
	}

	long now2 = System.currentTimeMillis();
	final CountDownLatch countDownLatch2 = new CountDownLatch(1);
	tcpClient.start().doOnError(err -> {
	    countDownLatch2.countDown();
	    System.out.println(System.currentTimeMillis() + ":client failed to start at " + portNumber);
	}).doOnSuccess(result -> {
	    countDownLatch2.countDown();
	    System.out.println(System.currentTimeMillis() + ":" + result);
	    System.out.println("Client starting took " + (System.currentTimeMillis() - now2));
	}).subscribe();
	try {
	    countDownLatch2.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    fail("Client failed to start");
	}

	long now4 = System.currentTimeMillis();
	final CountDownLatch countDownLatch4 = new CountDownLatch(1);
	tcpClient.stop().doOnError(err -> {
	    countDownLatch4.countDown();
	    System.out.println(System.currentTimeMillis() + ":client failed to stop");
	}).doOnSuccess(result -> {
	    countDownLatch4.countDown();
	    System.out.println(
		    System.currentTimeMillis() + ":client stops");
	    System.out.println("Client stopping took " + (System.currentTimeMillis() - now4));
	}).subscribe();
	try {
	    countDownLatch4.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    fail("Client failed to stop in time");
	}

	long now5 = System.currentTimeMillis();
	final CountDownLatch countDownLatch5 = new CountDownLatch(1);
	tcpClient.start().doOnError(err -> {
	    countDownLatch5.countDown();
	    System.out.println(System.currentTimeMillis() + ":client failed to start at " + portNumber);
	}).doOnSuccess(result -> {
	    countDownLatch5.countDown();
	    System.out.println(
		    System.currentTimeMillis() + ":client starts again at " + portNumber);
	    System.out.println("Client starting again took " + (System.currentTimeMillis() - now5));
	}).subscribe();
	try {
	    countDownLatch5.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    fail("Client failed to start again");
	}

	long now6 = System.currentTimeMillis();
	final CountDownLatch countDownLatch6 = new CountDownLatch(1);
	tcpServer.send("ABC".getBytes(), serverLinks.get(serverLinks.size() - 1).getRemote()).doOnError(err -> {
	    countDownLatch6.countDown();
	    System.out.println(">>> server failed to send to " + serverLinks.get(serverLinks.size() - 1).getRemote());
	}).doOnSuccess(result -> {
	    countDownLatch6.countDown();
	    System.out.println(">>> server send() to " + serverLinks.get(serverLinks.size() - 1).getRemote());
	    System.out.println(">>> server send() took " + (System.currentTimeMillis() - now6));
	}).subscribe();
	try {
	    countDownLatch6.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    fail("server failed to send again");
	}

	long now7 = System.currentTimeMillis();
	final CountDownLatch countDownLatch7 = new CountDownLatch(1);
	tcpClient.send("123".getBytes(), clientLinks.get(clientLinks.size() - 1).getRemote()).doOnError(err -> {
	    countDownLatch7.countDown();
	    System.out.println(">>> client failed to send to " + clientLinks.get(clientLinks.size() - 1).getRemote());
	}).doOnSuccess(result -> {
	    countDownLatch7.countDown();
	    System.out.println(">>> client send() to " + clientLinks.get(clientLinks.size() - 1).getRemote());
	    System.out.println(">>> client send() took " + (System.currentTimeMillis() - now7));
	}).subscribe();
	try {
	    countDownLatch7.await(5, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    fail("client failed to send again");
	}

	System.out.println("...");
	Thread.sleep(5000);
	System.out.println("...");

//	tcpServer.close();

//	
//	try {
//	    countDownLatch1.await(5, TimeUnit.SECONDS);
//	} catch (InterruptedException e) {
//	    fail("Server failed to start");
//	}

//	tcpServer.state().doOnError(error -> {
//	    fail("Shouldn't throw an error");
//	}).doOnNext(status -> {
//	    System.out.println("Server status updated: " + status);
////	    serverStatus.add(status);
//	}).doOnComplete(new Runnable() {
//	    @Override
//	    public void run() {
//		System.out.println("Server status completed");
//	    }
//	}).subscribe(status -> {
//	    
//	});

//	Thread.sleep(5000);

//	final CountDownLatch countDownLatch2 = new CountDownLatch(1);
//	tcpServer.open().doOnError(err -> {
//	    countDownLatch2.countDown();
//	    System.out.println("Server failed to open at " + portNumber);
//	}).doOnSuccess(__ -> {
//	    countDownLatch2.countDown();
//	    System.out.println("Server opened at " + portNumber);
//	}).subscribe();
//	
//	try {
//	    countDownLatch2.await(5, TimeUnit.SECONDS);
//	} catch (InterruptedException e) {
//	    fail("Server failed to open");
//	}

//	Thread.sleep(5000);

//		Fluxer tcpServer = new TcpServerFluxer("127.0.0.1", portNumber);
//		List<Fluxer.State> serverStatus = new ArrayList<>();
//		List<Fluxer.Link> serverLink = new ArrayList<>();
//		List<Fluxer.Message> serverInbound = new ArrayList<>();
//		Fluxer tcpClient = new TcpClientFluxer("127.0.0.1", portNumber);
//		List<Fluxer.State> clientStatus = new ArrayList<>();
//		List<Fluxer.Link> clientLink = new ArrayList<>();
//		List<Fluxer.Message> clientInbound = new ArrayList<>();
//
//		final CountDownLatch countDownLatch1 = new CountDownLatch(1);
//		tcpServer.start().doOnError(err -> {
//			countDownLatch1.countDown();
//			System.out.println("Server failed to start at " + portNumber);
//		}).doOnSuccess(__ -> {
//			countDownLatch1.countDown();
//			System.out.println("Server started at " + portNumber);
//		}).subscribe();
//		try {
//			countDownLatch1.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Server failed to start");
//		}
//
//		final CountDownLatch countDownLatch2 = new CountDownLatch(1);
//		tcpClient.start().doOnError(rr -> {
//			countDownLatch2.countDown();
//			System.out.println("Client failed to start at " + portNumber);
//		}).doOnSuccess(__ -> {
//			countDownLatch2.countDown();
//			System.out.println("Client started");
//		}).subscribe();
//		try {
//			countDownLatch2.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Client failed to start");
//		}
//
//		tcpServer.link().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(link -> {
//			System.out.println("Server link updated: " + link);
//			serverLink.add(link);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("Server link completed and total " + serverLink.size() + " received");
//			}
//		}).subscribe();
//		
//		tcpServer.state().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(status -> {
//			System.out.println("Server status updated: " + status);
//			serverStatus.add(status);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("Server status completed and total " + serverStatus.size() + " received");
//			}
//		}).subscribe();
//
//		tcpServer.read().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(message -> {
//			System.out.println("subscriber 1 | Server message received: " + message);
//			serverInbound.add(message);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("subscriber 1 | Server message completed and total " + serverInbound.size() + " received");
//			}
//		}).subscribe();
//		
//		tcpServer.read().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(message -> {
//			System.out.println("subscriber 2 | Server message received: " + message);
//			serverInbound.add(message);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("subscriber 2 | Server message completed and total " + serverInbound.size() + " received");
//			}
//		}).subscribe();
//
//		tcpClient.link().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(link -> {
//			System.out.println("Client link updated: " + link);
//			clientLink.add(link);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("Client link completed and total " + clientLink.size() + " received");
//			}
//		}).subscribe();
//
//		tcpClient.state().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(status -> {
//			System.out.println("Client status updated: " + status);
//			clientStatus.add(status);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("Client status completed and total " + clientStatus.size() + " received");
//			}
//		}).subscribe();
//
//		tcpClient.read().doOnError(error -> {
//			fail("Shouldn't throw an error");
//		}).doOnNext(message -> {
//			System.out.println("Client message received: " + message);
//			clientInbound.add(message);
//		}).doOnComplete(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("Client message completed and total " + clientInbound.size() + " received");
//			}
//		}).subscribe();

//	    int leftLimit = 48; // numeral '0'
//	    int rightLimit = 122; // letter 'z'
//	    int targetStringLength = 1000;
//	    Random random = new Random();
//	    String generatedString = random.ints(leftLimit, rightLimit + 1)
//	      .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
//	      .limit(targetStringLength)
//	      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
//	      .toString();
//		final CountDownLatch countDownLatch3 = new CountDownLatch(1);
//		tcpServer.write(new Message(serverLink.get(0), "1234".getBytes())).doOnError(error -> {
//			countDownLatch3.countDown();
//			System.out.println("Writing to server failed");
//		}).doOnSuccess(__ -> {
//			countDownLatch3.countDown();
//			System.out.println("Writing to server done");
//		}).subscribe();
//		try {
//			countDownLatch3.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Writing to server failed");
//		}

//		final CountDownLatch countDownLatch3 = new CountDownLatch(1);
//		tcpClient.write(new Message(clientLink.get(0), "1234".getBytes())).doOnError(error -> {
//			countDownLatch3.countDown();
//			System.out.println("Writing to client failed");
//		}).doOnSuccess(__ -> {
//			countDownLatch3.countDown();
//			System.out.println("Writing to client done");
//		}).subscribe();
//		try {
//			countDownLatch3.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Writing to client failed");
//		}
//		
//		// Received messages might be merged. 
//		// Sleep for a few msec(1 msec doesn't work?) to make sure they are published separately
//		try {
//			Thread.sleep(2L);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//		
//		final CountDownLatch countDownLatch4 = new CountDownLatch(1);
//		tcpClient.write(new Message(clientLink.get(0), "5678".getBytes())).doOnError(error -> {
//			countDownLatch4.countDown();
//			System.out.println("Writing to client failed");
//		}).doOnSuccess(__ -> {
//			countDownLatch4.countDown();
//			System.out.println("Writing to client done");
//		}).subscribe();
//		try {
//			countDownLatch4.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Writing to client failed");
//		}
//
//
//
//
//		final CountDownLatch countDownLatch5 = new CountDownLatch(1);
//		tcpClient.stop().doOnError(ex -> {
//			System.out.println("Client failed to stop");
//		}).doOnSuccess(__ -> {
//			countDownLatch5.countDown();
//			System.out.println("Client stopped");
//		}).subscribe();
//		try {
//			countDownLatch5.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Client failed to stop");
//		}
//
//		final CountDownLatch countDownLatch6 = new CountDownLatch(1);
//		tcpServer.stop().doOnError(ex -> {
//			System.out.println("Server failed to stopp");
//		}).doOnSuccess(__ -> {
//			countDownLatch6.countDown();
//			System.out.println("Server stopped");
//		}).subscribe();
//		try {
//			countDownLatch6.await(1, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			fail("Server failed to stop");
//		}
    }
}
