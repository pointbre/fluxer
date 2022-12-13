package com.github.pointbre.core;

public enum Status {
    // Server: Just created or shutdown finished
    STOPPED,
    // Server: Biding started
    STARTING,
    // Server: Binding done, waiting for client's connection
    CONNECTING,
    // Server: Client is connected
    CONNECTED,
    // Server: initiated shutdown
    STOPPING;
}
