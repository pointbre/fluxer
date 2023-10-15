package com.github.pointbre.fluxer.core;

import com.github.pointbre.fluxer.core.Fluxer.EndPoint;
import com.github.pointbre.fluxer.core.Fluxer.Result;

import reactor.core.publisher.Mono;

public interface ServerFluxer {

    Mono<Result> disconnect(EndPoint remote);

}