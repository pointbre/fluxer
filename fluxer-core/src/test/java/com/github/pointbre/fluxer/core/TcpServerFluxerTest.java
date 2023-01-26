package com.github.pointbre.fluxer.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import reactor.core.Disposable;

@ExtendWith(MockitoExtension.class)
@ExtendWith(Slf4jExtension.class)
@ExtendWith(PortNumberExtension.class)
class TcpServerFluxerTest {

	private Logger logger;
	private Integer portNumber;
	
	private Disposable statusSubscription = null;
	private Disposable linkSubscription = null;
	private Disposable inboundSubscription = null;

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
				.doOnError(ex -> System.out.println("doOnError " + ex))
				.doOnSuccess(__ -> {
					System.out.println("doOnSuccess: portNumber=" + portNumber);
					System.out.println("subscribing to streams");
					statusSubscription = fluxer.status().subscribe(s -> logger.debug("Status changed: " + s));
					linkSubscription = fluxer.link().subscribe(s -> logger.debug("Link changed: " + s));
					inboundSubscription = fluxer.read().subscribe(m -> logger.debug("Inbound changed: " + m));
					countDownLatch1.countDown();
				})
				.subscribe();

		try {
			System.out.println("Waiting");
			countDownLatch1.await();
			System.out.println("Done");
		} catch (InterruptedException e) {
			fail("Server failed to start");
		}

		final CountDownLatch countDownLatch2 = new CountDownLatch(1);
		try {
			countDownLatch2.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//
//		fluxer.stop().subscribe();
//		
//		if (statusSubscription != null) {
//			statusSubscription.dispose();
//			statusSubscription = null;
//		}
//		if (linkSubscription != null) {
//			linkSubscription.dispose();
//			linkSubscription = null;
//		}
//		if (inboundSubscription != null) {
//			inboundSubscription.dispose();
//			inboundSubscription = null;
//		}
//
//		try {
//			assertTrue(countDownLatch1.await(5, TimeUnit.SECONDS));
//		} catch (InterruptedException e) {
//			fail("Server failed to start");
//		}
		
		

//		serverFluxer.stop();
	}

	// See reactor's test section about step verifier and so on
	// See reactor-netty's unit tests
}
