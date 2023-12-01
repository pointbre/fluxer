package com.github.pointbre.asyncer.core;

import java.util.List;
import java.util.UUID;

import lombok.NonNull;
import lombok.Value;
import reactor.util.annotation.Nullable;

@Value
public class TransitionResult<S extends State<T>, T, E extends Event<F>, F, R> {

    @NonNull
    UUID uuid;

    @NonNull
    E event;

    @Nullable
    List<S> states;

    @Nullable
    Transition<S, T, E, F, R> transition;

    @Nullable
    List<TaskResult<R>> taskResults;

    @NonNull
    R result;

    @NonNull
    String description;

}