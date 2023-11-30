package com.github.pointbre.asyncer.core;

import java.util.UUID;

import lombok.NonNull;
import lombok.Value;

@Value
public class TaskResult<R> {

    @NonNull
    UUID uuid;

    @NonNull
    R result;

    @NonNull
    String description;

}
