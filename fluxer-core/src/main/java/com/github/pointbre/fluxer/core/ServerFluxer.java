package com.github.pointbre.fluxer.core;

import reactor.core.publisher.Mono;

public interface ServerFluxer<T> extends TcpFluxer<T> {

    Mono<RequestResult> disconnect(EndPoint remote);

}