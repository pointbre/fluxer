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
	private Link link;
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
	
    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        System.out.println("LinkService PostConstruct");
		link = new TcpServerLink();
		linkStatusSubscription = link.getLinkStatusStream().subscribe(s -> System.out.println("Status changed: " + s));
		inboundMessageSubscription = link.getInboundMessageStream().subscribe(m -> System.out.println("Message received: " + m));

    }

    @PreDestroy
    public void destroy() throws Exception {
    	System.out.println("LinkService PreDestroy");
    	
    	if (linkStatusSubscription != null) {
    		linkStatusSubscription.dispose();
    	}
    	if (inboundMessageSubscription != null) {
    		inboundMessageSubscription.dispose();
    	}
        System.out.println("LinkService destroyed");
    }
}
