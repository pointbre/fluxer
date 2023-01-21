package com.github.pointbre.fluxer.cli;

import com.github.pointbre.fluxer.core.TcpServerFluxer;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
//@Service
public class TcpServerService {
    private TcpServerFluxer fluxer;
    private Disposable linkStatusSubscription;
    private Disposable inboundMessageSubscription;

    public TcpServerService() {
	fluxer = TcpServerFluxer.builder().host("localhost").port(8421).logging(true).build();
    }

//    @PostConstruct
    public void init() {
	fluxer.start().then().doOnError(ex -> {
	}).doOnSuccess(__ -> {
	    log.debug("subscribing to streams");
	    linkStatusSubscription = fluxer.status().subscribe(s -> log.debug("Status changed: " + s));
	    inboundMessageSubscription = fluxer.read().subscribe(m -> log.debug("Message received: " + m));
	}).doFinally(__ -> {
	}).subscribe(x -> {
	}, ex -> {
	});
    }

//    @PreDestroy
    public void destory() {
	fluxer.stop().then().doFinally(__ -> {
	    log.debug("link destroyed");

	    log.debug("disposing subscriptions to streams");
	    if (linkStatusSubscription != null) {
		linkStatusSubscription.dispose();
		linkStatusSubscription = null;
	    }
	    if (inboundMessageSubscription != null) {
		inboundMessageSubscription.dispose();
		inboundMessageSubscription = null;
	    }
	}).subscribe(x -> {
	}, ex -> {
	});
    }

}