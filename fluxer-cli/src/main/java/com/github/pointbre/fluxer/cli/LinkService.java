package com.github.pointbre.fluxer.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.pointbre.fluxer.core.Fluxer;
import com.github.pointbre.fluxer.core.TcpServerFluxer;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;


@Service
@Slf4j
public class LinkService {
	
	private Fluxer fluxer = TcpServerFluxer.builder().build();
	private Disposable linkStatusSubscription;
	private Disposable inboundMessageSubscription;
	
	public Mono<Void> initialize() {
		Sinks.One<Void> resultSink = Sinks.one();
		
		fluxer.initialize().then()
		.doOnError(ex -> {
			resultSink.tryEmitError(ex);
		})
		.doOnSuccess(__ -> {
			log.debug("link initialized");
			log.debug("subscribing to streams");
			linkStatusSubscription = fluxer.monitor().subscribe(s -> log.debug("Status changed: " + s));
			inboundMessageSubscription = fluxer.read().subscribe(m -> log.debug("Message received: " + m));			
			resultSink.tryEmitEmpty();
		})
		.subscribe(x -> {}, ex -> {});
		
		return resultSink.asMono();
	}
	
	public Mono<Void> destroy() {
		Sinks.One<Void> resultSink = Sinks.one();
		
    	fluxer.destroy().then()
    	.doOnError(ex -> {
    		resultSink.tryEmitError(ex);
    	})
    	.doOnSuccess(__ -> {
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
        	resultSink.tryEmitEmpty();
    	})
    	.subscribe(x -> {}, ex -> {});
    	
    	return resultSink.asMono();
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
