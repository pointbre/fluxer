package com.github.pointbre.fluxer.core;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.AsyncerUtil;

import lombok.NonNull;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public interface Fluxer<T> extends AutoCloseable {

	/**
	 * Fluxer's UUID
	 * 
	 * @return
	 */
	UUID uuid();

	/**
	 * Starts up Fluxer
	 * 
	 * @return {@link Mono} of {@link RequestResult}
	 */
	Mono<RequestResult> start();

	/**
	 * Stops Fluxer
	 * 
	 * @return {@link Mono} of {@link RequestResult}
	 */
	Mono<RequestResult> stop();

	/**
	 * Send the byte array to the remote {@link EndPoint}
	 * 
	 * @param message
	 * @param remote
	 * @return
	 */
	Mono<RequestResult> send(T message, EndPoint remote);

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
	public class RequestResult {

		@NonNull
		UUID uuid;

		@NonNull
		State.Event event;

		@NonNull
		Boolean result;

		@NonNull
		String description;
	}

	@Value
	public class State {
		public enum Type {
			STARTING, STARTED, STOPPING, STOPPED
		}

		public enum Event {
			START, STOP, SEND;
		}

		/**
		 * UUID of the state change
		 */
		@NonNull
		UUID uuid;

		@NonNull
		Type type;

		@Nullable
		Event event;

		public State(UUID uuid, Type type, Event event) {
			this.uuid = uuid;
			this.type = type;
			this.event = event;
		}

		public State(Type type, Event event) {
			this.uuid = AsyncerUtil.generateType1UUID();
			this.type = type;
			this.event = event;
		}

		public State(Type type) {
			this(type, null);
		}
	}

	@Value
	public class Link {
		public enum State {
			CONNECTED, DISCONNECTED, NONE;
		}

		/**
		 * UUID of the state change
		 */
		@NonNull
		UUID uuid;

		/**
		 * The unique link id
		 */
		@NonNull
		String id;

		@NonNull
		State state;

		@NonNull
		EndPoint local;

		@NonNull
		EndPoint remote;

		public Link(String id, State state, EndPoint local, EndPoint remote) {
			this.uuid = AsyncerUtil.generateType1UUID();
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
		UUID uuid;

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
			this.uuid = AsyncerUtil.generateType1UUID();
			this.type = type;
			this.local = local;
			this.remote = remote;
			this.message = message;
		}
	}

	@Value
	public class Log {
		@NonNull
		UUID uuid;

		@NonNull
		LocalDateTime dateTime;

		@NonNull
		Level level;

		@NonNull
		String description;

		@Nullable
		Throwable throwable;

		public Log(Level level, String log, Throwable throwable) {
			this.uuid = AsyncerUtil.generateType1UUID();
			this.dateTime = LocalDateTime.now();
			this.level = level;
			this.description = log;
			this.throwable = throwable;
		}

		public Log(Level level, String log) {
			this(level, log, null);
		}
	}
}
