package com.github.pointbre.asyncer.core;

import java.util.UUID;

import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Asyncer<S extends State<T>, T, E extends Event<F>, F, R> extends AutoCloseable {

	UUID uuid();

	Mono<TransitionResult<S, T, E, F, R>> fire(@NonNull UUID uuid, @NonNull E event);

	Flux<StateChange<S>> stateChange();

	Flux<TransitionResult<S, T, E, F, R>> transitionResult();

}
