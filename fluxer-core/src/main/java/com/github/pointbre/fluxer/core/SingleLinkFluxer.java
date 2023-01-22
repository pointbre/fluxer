package com.github.pointbre.fluxer.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SingleFluxer extends Fluxer {
    
    Flux<Link> link();
    
    Flux<Message> read();

    Mono<Void> write(Message message);
    
}
