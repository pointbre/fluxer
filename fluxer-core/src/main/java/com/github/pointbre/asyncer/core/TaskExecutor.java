package com.github.pointbre.asyncer.core;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;

import lombok.NonNull;
import reactor.util.annotation.Nullable;

public sealed interface TaskExecutor<S extends State<S>, E extends Event<E>, R>
        extends AutoCloseable
        permits ParallelFAETaskExecutor, SequentialFAETaskExecutor {

    public List<TaskResult<R>> run(@NonNull S state, @NonNull E event,
            @NonNull List<BiFunction<S, E, TaskResult<R>>> tasks, @Nullable Duration timeout);
}