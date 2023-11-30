package com.github.pointbre.asyncer.core;

import java.util.UUID;

import lombok.NonNull;
import reactor.core.publisher.Sinks.Many;

public sealed interface TransitionExecutor<S extends State<S>, E extends Event<E>, R>
        extends AutoCloseable permits DefaultTransitionExecutorImpl {

    public TransitionResult<S, E, R> run(@NonNull UUID uuid, @NonNull S state, @NonNull E event,
            @NonNull Transition<S, E, R> transition, @NonNull Many<StateChange<S>> stateSink);
}