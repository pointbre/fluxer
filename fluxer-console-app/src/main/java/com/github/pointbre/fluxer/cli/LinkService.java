package com.github.pointbre.fluxer.cli;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.github.pointbre.fluxer.core.Link;
import com.github.pointbre.fluxer.core.TcpServerLink;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Service
public class LinkService {
	private Link link = new TcpServerLink();
	Disposable linkStatusSubscription;
	Disposable inboundMessageSubscription;
	
	public LinkService() {
	}

	public Mono<Void> start() {
		return link.start();
	}

	public Mono<Void> stop() {
		return link.stop();
	}
	
	public Mono<Void> initialize() {
		link.initialize().then().doFinally(signal -> {
			System.out.println("link initialized");
			System.out.println("subscribing to streams");
			linkStatusSubscription = link.getLinkStatusStream().subscribe(s -> System.out.println("Status changed: " + s));
			inboundMessageSubscription = link.getInboundMessageStream().subscribe(m -> System.out.println("Message received: " + m));
		}).subscribe(__ -> {}, ex -> {});
		
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
    	}).subscribe(__ -> {}, ex -> {});
    	
    	return Mono.<Void>empty();
	}
	
//    @PostConstruct
//    public void postConstruct() throws Exception {
//        System.out.println("LinkService PostConstruct-start");
//        
//		link.initialize().then().doFinally(signal -> {
//			System.out.println("link initialized");
//			System.out.println("subscribing to streams");
//			linkStatusSubscription = link.getLinkStatusStream().subscribe(s -> System.out.println("Status changed: " + s));
//			inboundMessageSubscription = link.getInboundMessageStream().subscribe(m -> System.out.println("Message received: " + m));
//		}).subscribe(__ -> {}, ex -> {});
//
//		System.out.println("LinkService PostConstruct-end");
//    }

//    @PreDestroy
//    public void preDestroy() throws Exception {
//    	System.out.println("LinkService PreDestroy-start");
//    	
//    	link.destroy().then().doFinally(signal -> {
//    		System.out.println("link destroyed");
//    		
//    		System.out.println("disposing subscriptions to streams");
//        	if (linkStatusSubscription != null) {
//        		linkStatusSubscription.dispose();
//        		linkStatusSubscription = null;
//        	}
//        	if (inboundMessageSubscription != null) {
//        		inboundMessageSubscription.dispose();
//        		inboundMessageSubscription = null;
//        	}
//    	}).subscribe(__ -> {}, ex -> {});
//    	
//        System.out.println("LinkService PreDestroy-end");
//    }
}
