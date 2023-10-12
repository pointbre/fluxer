package com.github.pointbre.fluxer.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Fluxer extends AutoCloseable {
    Mono<Result> start();

    Mono<Result> stop();

    Mono<Result> send(byte[] message, Endpoint remote);

    Flux<State> state();

    Flux<Link> link();

    Flux<Message> message();

    public enum State {
	STARTING, STARTED, STOPPING, STOPPED;
    }

    public enum Event {
	START_REQUESTED, STOP_REQUESTED, PROCESSED, FAILED;
    }

    public enum Result {
	PROCESSED, FAILED
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    public class Endpoint {
	@NonNull
	private String ipAddress;
	@NonNull
	private Integer port;
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    public class Link {
	public enum State {
	    CONNECTED, DISCONNECTED, NONE;
	}

	@NonNull
	private State state;
	private Endpoint local;
	private Endpoint remote;
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    public class Message {
	public enum Type {
	    INBOUND, OUTBOUND;
	}

	@NonNull
	private Type type;
	@NonNull
	private Link link;
	@NonNull
	private byte[] message;
    }
}
