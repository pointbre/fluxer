package com.github.pointbre.fluxer.cli;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.github.pointbre.fluxer.core.Fluxer;
import com.github.pointbre.fluxer.core.TcpServerFluxer;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

//@Service
@Slf4j
public class LinkService {

//    private SingleLinkFluxer fluxer = TcpServerFluxer.builder().build();
//    private Disposable linkStatusSubscription;
//    private Disposable inboundMessageSubscription;
//
//    public Mono<Void> start() {
//	Sinks.One<Void> resultSink = Sinks.one();
//
//	fluxer.start().then().doOnError(ex -> {
//	    resultSink.tryEmitError(ex);
//	}).doOnSuccess(__ -> {
//	    log.debug("link initialized");
//	    log.debug("subscribing to streams");
//	    linkStatusSubscription = fluxer.status().subscribe(s -> log.debug("Status changed: " + s));
//	    inboundMessageSubscription = fluxer.read().subscribe(m -> log.debug("Message received: " + m));
//	    resultSink.tryEmitEmpty();
//	}).subscribe(x -> {
//	}, ex -> {
//	});
//
//	return resultSink.asMono();
//    }
//
//    public Mono<Void> stop() {
//	Sinks.One<Void> resultSink = Sinks.one();
//
//	fluxer.stop().then().doOnError(ex -> {
//	    resultSink.tryEmitError(ex);
//	}).doOnSuccess(__ -> {
//	    log.debug("link destroyed");
//
//	    log.debug("disposing subscriptions to streams");
//	    if (linkStatusSubscription != null) {
//		linkStatusSubscription.dispose();
//		linkStatusSubscription = null;
//	    }
//	    if (inboundMessageSubscription != null) {
//		inboundMessageSubscription.dispose();
//		inboundMessageSubscription = null;
//	    }
//	    resultSink.tryEmitEmpty();
//	}).subscribe(x -> {
//	}, ex -> {
//	});
//
//	return resultSink.asMono();
//    }
//
//    public Mono<Void> write(byte[] message) {
//	return fluxer.write(message);
//    }

	Fluxer fluxer = null;
	private Disposable statusSubscription = null;
	private Disposable linkSubscription = null;
	private Disposable inboundSubscription = null;

	@PostConstruct
	public void postConstruct() {
		Fluxer fluxer = new TcpServerFluxer("localhost", 4501);

		fluxer.start()
				.doOnError(ex -> log.debug("doOnError " + ex))
				.doOnSuccess(__ -> {
					log.debug("doOnSuccess");
					log.debug("subscribing to streams");
					statusSubscription = fluxer.status().subscribe(s -> log.debug("Status changed: " + s));
					linkSubscription = fluxer.link().subscribe(s -> log.debug("Link changed: " + s));
					inboundSubscription = fluxer.read().subscribe(m -> log.debug("Inbound changed: " + m));
				})
				.subscribe();
	}

	@PreDestroy
	public void preDestroy() {
		if (fluxer != null) {
			fluxer.stop().subscribe();			
		}
		if (statusSubscription != null) {
			statusSubscription.dispose();
			statusSubscription = null;
		}
		if (linkSubscription != null) {
			linkSubscription.dispose();
			linkSubscription = null;
		}
		if (inboundSubscription != null) {
			inboundSubscription.dispose();
			inboundSubscription = null;
		}
	}
}
