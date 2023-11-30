package com.github.pointbre.asyncer.core;

import java.util.UUID;

import lombok.NonNull;
import lombok.Value;

@Value
public class StateChange<S> {
    @NonNull
    UUID uuid;

    @NonNull
    S state;
}
