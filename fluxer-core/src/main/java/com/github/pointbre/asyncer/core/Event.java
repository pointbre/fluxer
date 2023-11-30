package com.github.pointbre.asyncer.core;

public class Event<T> extends Typed<T> {

    protected Event(T type) {
        super(type);
    }

}