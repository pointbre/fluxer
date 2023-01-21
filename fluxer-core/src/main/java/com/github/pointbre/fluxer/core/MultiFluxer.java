package com.github.pointbre.fluxer.core;

import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MultiFluxer extends Fluxer {
    
    Flux<List<Link>> link();
    
    Flux<Message> read(Link link);

    Mono<Void> write(Link link, Message message);
    
}
