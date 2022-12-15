package com.github.pointbre.fluxer.cli;

import org.springframework.stereotype.Service;

import com.github.pointbre.fluxer.core.Link;
import com.github.pointbre.fluxer.core.TcpServerLink;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Service
public class LinkService {
	private Link link = new TcpServerLink();
	private Disposable linkStatusSubscription;
	private Disposable inboundMessageSubscription;
	
	public Mono<Void> initialize() {
		link.initialize().then().doFinally(signal -> {
			System.out.println("link initialized");
			System.out.println("subscribing to streams");
			linkStatusSubscription = link.getLinkStatusStream().subscribe(s -> System.out.println("Status changed: " + s));
			inboundMessageSubscription = link.getInboundMessageStream().subscribe(m -> System.out.println("Message received: " + m));
		}).subscribe(x -> {}, ex -> {});
		
		return Mono.<Void>empty();
	}
	
	public Mono<Void> destroy() {
    	link.destroy().then().doFinally(signal -> {
    		System.out.println("link destroyed");
    		
    		System.out.println("disposing subscriptions to streams");
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
		return link.start();
	}

	public Mono<Void> stop() {
		return link.stop();
	}
}
