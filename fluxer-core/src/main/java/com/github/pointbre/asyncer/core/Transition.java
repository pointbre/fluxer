package com.github.pointbre.asyncer.core;

import lombok.Value;

@Value
public class Transition<S extends State<T>, T, E extends Event<F>, F, R> {

    // @NonNull
    // String name;

    // @NonNull
    S from;

    // @NonNull
    E event;

    // @Nullable
    // S to;

    // @Nullable
    // List<BiFunction<S, E, TaskResult<R>>> tasks;

    // @Nullable
    // TaskExecutor<S, E, R> taskExecutor;

    // @Nullable
    // Duration timeout;

    // @Nullable
    // S toWhenProcessed;

    // @Nullable
    // S toWhenFailed;
}