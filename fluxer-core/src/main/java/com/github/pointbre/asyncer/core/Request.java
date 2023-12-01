package com.github.pointbre.asyncer.core;

import java.util.UUID;

import reactor.core.publisher.Sinks.One;

public record Request<S extends State<T>, T, E extends Event<F>, F>(UUID uuid, E event,
        One<TransitionResult<S, T, E, F, Boolean>> resultSink) {
}