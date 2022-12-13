package com.github.pointbre.core;

import java.nio.ByteBuffer;

import reactor.core.publisher.Flux;

public interface Link {
    Flux<Status> getLinkStatusStream();
    Flux<ByteBuffer> getInboundStream();
    void setOutboundStream(Flux<ByteBuffer> outboundStream);
}
