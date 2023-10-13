package com.github.pointbre.fluxer.core;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Fluxer extends AutoCloseable {
    /**
     * Starts up Fluxer
     * 
     * @return {@link Mono} of {@link Result}
     */
    Mono<Result> start();

    /**
     * Stops Fluxer
     * 
     * @return {@link Mono} of {@link Result}
     */
    Mono<Result> stop();

    /**
     * Send the byte array to the remote {@link EndPoint}
     * 
     * @param message
     * @param remote
     * @return
     */
    Mono<Result> send(byte[] message, EndPoint remote);

    /**
     * Provides the stream of {@link State} changes
     * 
     * @return {@link Flux} of {@link State}
     */
    Flux<State> state();

    /**
     * Provides the stream of {@link Link} changes
     * 
     * @return {@link Flux} of {@link Link}
     */
    Flux<Link> link();

    /**
     * Provides the stream of inbound and outbound {@link Message} changes
     * 
     * @return {@link Flux} of {@link Message}
     */
    Flux<Message> message();

    public enum State {
	STARTING, STARTED, STOPPING, STOPPED;
    }

    public enum Event {
	START_REQUESTED, STOP_REQUESTED, PROCESSED, FAILED;
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    @EqualsAndHashCode
    public class Result {
	public enum Type {
	    PROCESSED, FAILED
	}
	
	@NonNull
	private Type type;
	@NonNull
	private String description;
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    @EqualsAndHashCode
    public class EndPoint {
	@NonNull
	private String ipAddress;
	@NonNull
	private Integer port;
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    @EqualsAndHashCode
    public class Link {
	public enum State {
	    CONNECTED, DISCONNECTED, NONE;
	}

	@NonNull
	private State state;
	private EndPoint local;
	private EndPoint remote;
    }

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    @EqualsAndHashCode
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

    @Value
    @AllArgsConstructor
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public class FluxerException extends Exception {
	private static final long serialVersionUID = 7341500687389967346L;

	@NonNull
	private String description;
    }
}
