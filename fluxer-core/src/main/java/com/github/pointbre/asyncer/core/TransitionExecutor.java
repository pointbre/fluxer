package com.github.pointbre.asyncer.core;

import java.util.UUID;

import lombok.NonNull;
import reactor.core.publisher.Sinks.Many;

public sealed interface TransitionExecutor<S extends State<T>, T, E extends Event<F>, F, R>
                extends AutoCloseable permits DefaultTransitionExecutorImpl {

        public TransitionResult<S, T, E, F, R> run(@NonNull UUID uuid,
                        @NonNull S state, @NonNull E event,
                        @NonNull Transition<S, T, E, F, R> transition,
                        @NonNull Many<StateChange<S>> stateSink);
}