package com.github.pointbre.fluxer.core;

import org.slf4j.event.Level;

import com.github.pointbre.fluxer.util.FluxerUtil;

import lombok.NonNull;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Fluxer<T> extends AutoCloseable {
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
    Mono<Result> send(T message, EndPoint remote);

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
    Flux<Message<T>> message();

    /**
     * Provides the stream of Fluxer's {@link Log} changes
     * 
     * @return {@link Flux} of {@link Log}
     */
    Flux<Log> log();

    @Value
    public class Result {
	public enum Type {
	    PROCESSED, FAILED
	}

	@NonNull
	String uuid;
	@NonNull
	Type type;
	@NonNull
	String description;

	public Result(Type type, String description) {
	    this.uuid = FluxerUtil.generateType1UUID().toString();
	    this.type = type;
	    this.description = description;
	}
    }

    @Value
    public class State {
	public enum Type {
	    STARTING, STARTED, STOPPING, STOPPED
	}

	public enum Event {
	    START_REQUESTED, STOP_REQUESTED, PROCESSED, FAILED;
	}

	@NonNull
	String uuid;
	@NonNull
	String id;
	@NonNull
	Type type;
	Event event;

	public State(String id, Type type, Event event) {
	    this.uuid = FluxerUtil.generateType1UUID().toString();
	    this.id = id;
	    this.type = type;
	    this.event = event;
	}

	public State(String id, Type type) {
	    this(id, type, null);
	}
    }

    @Value
    public class Link {
	public enum State {
	    CONNECTED, DISCONNECTED, NONE;
	}

	@NonNull
	String uuid;
	@NonNull
	String id;
	@NonNull
	State state;
	@NonNull
	EndPoint local;
	@NonNull
	EndPoint remote;

	public Link(String id, State state, EndPoint local, EndPoint remote) {
	    this.uuid = FluxerUtil.generateType1UUID().toString();
	    this.id = id;
	    this.state = state;
	    this.local = local;
	    this.remote = remote;
	}
    }

    @Value
    public class EndPoint {
	@NonNull
	String ipAddress;
	@NonNull
	Integer port;

	public EndPoint(String ipAddress, Integer port) {
	    this.ipAddress = ipAddress;
	    this.port = port;
	}
    }

    @Value
    public class Message<T> {
	public enum Type {
	    INBOUND, OUTBOUND;
	}

	@NonNull
	String uuid;
	@NonNull
	Type type;
	@NonNull
	EndPoint local;
	@NonNull
	EndPoint remote;
	@NonNull
	T message;

	public static <T> Message<T> of(Type type, EndPoint local, EndPoint remote, T message) {
	    return new Message<>(type, local, remote, message);
	}

	private Message(Type type, EndPoint local, EndPoint remote, T message) {
	    this.uuid = FluxerUtil.generateType1UUID().toString();
	    this.type = type;
	    this.local = local;
	    this.remote = remote;
	    this.message = message;
	}
    }

    @Value
    public class Log {
	@NonNull
	String uuid;
	@NonNull
	Level level;
	@NonNull
	String log;

	public Log(Level level, String log) {
	    this.uuid = FluxerUtil.generateType1UUID().toString();
	    this.level = level;
	    this.log = log;
	}
    }
}
