package com.github.pointbre.fluxer.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Fluxer {
	Mono<Void> start();
    Mono<Void> stop();
    
    Flux<Status> monitor();
    Flux<byte[]> read();   
    Mono<Void> write(byte[] message);
}
