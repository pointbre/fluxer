package com.github.pointbre.fluxer.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Link {
	Mono<Void> initialize();
	Mono<Void> destroy();
	Mono<Void> start();
    Mono<Void> stop();
    
    Flux<Status> monitor();
    Flux<byte[]> read();   
    Mono<Void> write(byte[] message);
}
