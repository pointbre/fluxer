package com.github.pointbre.fluxer.core;

import java.util.List;
import java.util.UUID;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer;
import com.github.pointbre.asyncer.core.Asyncer.Change;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionResult;
import com.github.pointbre.asyncer.core.Asyncer.Typed;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public interface Fluxer<M> extends AutoCloseable {

	UUID uuid();

	Flux<Change<State>> state();

	Flux<Change<Link>> link();

	Flux<Change<Message<M>>> message();

	Flux<Change<Log>> log();

	Mono<RequestResult<M>> start();

	Mono<RequestResult<M>> stop();

	Mono<RequestResult<M>> send(@NonNull M message, @NonNull EndPoint remote);

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class State extends Asyncer.State<State.Type> {

		@ToString
		public enum Type {
			STARTING, STARTED, STOPPING, STOPPED
		}

		public State(Type type) {
			super(type);
		}

	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class Event<M> extends Asyncer.Event<Event.Type> {

		public enum Type {
			START, STOP, SEND;
		}

		public Event(Type type) {
			this(type, null, null);
		}

		public Event(Type type, @Nullable M message, @Nullable EndPoint remote) {
			super(type);
			this.message = message;
			this.remote = remote;
		}

		@Nullable
		M message;

		@Nullable
		EndPoint remote;

	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class Link extends Typed<Link.Type> {

		public enum Type {
			CONNECTED, DISCONNECTED, NONE;
		}

		/**
		 * The unique link id
		 */
		@NonNull
		String id;

		@NonNull
		EndPoint local;

		@NonNull
		EndPoint remote;

		public Link(@NonNull Type type, @NonNull String id, @NonNull EndPoint local,
				@NonNull EndPoint remote) {
			super(type);
			this.id = id;
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

	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class Message<M> extends Typed<Message.Type> {
		public enum Type {
			INBOUND, OUTBOUND;
		}

		@NonNull
		EndPoint local;

		@NonNull
		EndPoint remote;

		@NonNull
		M message;

		public Message(@NonNull Type type, @NonNull EndPoint local, @NonNull EndPoint remote, @NonNull M message) {
			super(type);
			this.local = local;
			this.remote = remote;
			this.message = message;
		}

	}

	@Value
	@EqualsAndHashCode(callSuper = true)
	public class Log extends Typed<Level> {

		@NonNull
		String description;

		@Nullable
		Throwable throwable;

		public Log(@NonNull Level level, @NonNull String description, @Nullable Throwable throwable) {
			super(level);
			this.description = description;
			this.throwable = throwable;
		}

	}

	@Value
	@NonFinal
	@EqualsAndHashCode(callSuper = true)
	public class RequestResult<M> extends TransitionResult<State, State.Type, Event<M>, Event.Type, Boolean> {

		public RequestResult(@NonNull UUID uuid, @NonNull Boolean value, @NonNull String description,
				@NonNull Event<M> event, @Nullable List<State> states,
				@Nullable Transition<State, State.Type, Event<M>, Event.Type, Boolean> transition,
				@Nullable List<Result<Boolean>> taskResults) {
			super(uuid, value, description, event, states, transition, taskResults);
		}

		public RequestResult(TransitionResult<State, State.Type, Event<M>, Event.Type, Boolean> transitionResult) {
			this(transitionResult.getUuid(), transitionResult.getValue(), transitionResult.getDescription(),
					transitionResult.getEvent(), transitionResult.getStates(), transitionResult.getTransition(),
					transitionResult.getTaskResults());
		}
	}

}
