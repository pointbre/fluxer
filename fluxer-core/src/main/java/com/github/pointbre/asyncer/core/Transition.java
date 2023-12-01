package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;

import lombok.NonNull;
import lombok.Value;
import reactor.util.annotation.Nullable;

@Value
public class Transition<S extends State<T>, T, E extends Event<F>, F, R> {

    @NonNull
    String name;

    @NonNull
    S from;

    @NonNull
    E event;

    @Nullable
    S to;

    @Nullable
    List<BiFunction<S, E, TaskResult<R>>> tasks;

    @Nullable
    TaskExecutor<S, T, E, F, R> taskExecutor;

    @Nullable
    Duration timeout;

    @Nullable
    S toWhenProcessed;

    @Nullable
    S toWhenFailed;
}