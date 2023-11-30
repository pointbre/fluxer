package com.github.pointbre.asyncer.core;

public abstract class Typed<T> {
    private final T type;

    protected Typed(T type) {
        this.type = type;
    }

    public T getType() {
        return type;
    }
}