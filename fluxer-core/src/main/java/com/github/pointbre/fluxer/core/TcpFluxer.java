package com.github.pointbre.fluxer.core;

public interface TcpFluxer<T> extends Fluxer<T> {
    String getIpAddress();

    Integer getPort();
}
