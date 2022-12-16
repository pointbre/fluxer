package com.github.pointbre.fluxer.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.pointbre.fluxer.core.Fluxer;
import com.github.pointbre.fluxer.core.TcpServerFluxer;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class LinkService {
	
	private Fluxer fluxer = TcpServerFluxer.builder().build();
	private Disposable linkStatusSubscription;
	private Disposable inboundMessageSubscription;
	
	public Mono<Void> initialize() {
		fluxer.initialize().then().doFinally(signal -> {
			log.debug("link initialized");
			log.debug("subscribing to streams");
			linkStatusSubscription = fluxer.monitor().subscribe(s -> log.debug("Status changed: " + s));
			inboundMessageSubscription = fluxer.read().subscribe(m -> log.debug("Message received: " + m));
		}).subscribe(x -> {}, ex -> {});
		
		return Mono.<Void>empty();
	}
	
	public Mono<Void> destroy() {
    	fluxer.destroy().then().doFinally(signal -> {
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
    	}).subscribe(x -> {}, ex -> {});
    	
    	return Mono.<Void>empty();
	}
	
	public Mono<Void> start() {
		return fluxer.start();
	}

	public Mono<Void> stop() {
		return fluxer.stop();
	}

	public Mono<Void> write(byte[] message) {
		return fluxer.write(message);
	}
}
