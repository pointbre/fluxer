package com.github.pointbre.fluxer.core;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer;
import com.github.pointbre.asyncer.core.AsyncerUtil;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public interface Fluxer<T> extends AutoCloseable {

	UUID uuid();

	Flux<Asyncer.Change<State>> state();

	Flux<Asyncer.Change<Link>> link();

	Flux<Asyncer.Change<Message<T>>> message();

	Flux<Asyncer.Change<Log>> log();

	Mono<RequestResult> start();

	Mono<RequestResult> stop();

	Mono<RequestResult> send(T message, EndPoint remote);

	@Value
	public class RequestResult extends Asyncer.Result<Boolean> {

		@NonNull
		State.Event event;

	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class State extends Asyncer.State<State.Type> {

		public enum Type {
			STARTING, STARTED, STOPPING, STOPPED
		}

		public State(Type type) {
			super(type);
		}

		@Override
		public String toString() {
			return this.getType().name();
		}
	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class Event extends Asyncer.Event<Event.Type> {
		public enum Type {
			START, STOP, SEND;
		}

		public Event(Type type) {
			super(type);
		}

		@Override
		public String toString() {
			return this.getType().name();
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
