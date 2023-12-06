/**
 * 
 */
package com.github.pointbre.fluxer.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.event.Level;

import com.github.pointbre.asyncer.core.Asyncer;
import com.github.pointbre.asyncer.core.Asyncer.Change;
import com.github.pointbre.asyncer.core.Asyncer.Result;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.AsyncerUtil;
import com.github.pointbre.asyncer.core.DefaultAsyncerImpl;
import com.github.pointbre.asyncer.core.DefaultTransitionExecutorImpl;
import com.github.pointbre.asyncer.core.SequentialFAETaskExecutorImpl;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

@Slf4j
public abstract class AbstractFluxer<M> implements Fluxer<M> {

	private final UUID uuid = AsyncerUtil.generateType1UUID();

	private Many<Change<State>> stateSink;
	private Many<Change<Link>> linkSink;
	private Many<Change<Message<M>>> messageSink;
	private Many<Change<Log>> logSink;

	private Flux<Change<State>> stateFlux;
	private Flux<Change<Link>> linkFlux;
	private Flux<Change<Message<M>>> messageFlux;
	private Flux<Change<Log>> logFlux;

	protected Asyncer<State, State.Type, Event<M>, Event.Type, Boolean> asyncer;

	protected final State STARTING = new State(State.Type.STARTING);
	protected final State STARTED = new State(State.Type.STARTED);
	protected final State STOPPING = new State(State.Type.STOPPING);
	protected final State STOPPED = new State(State.Type.STOPPED);

	protected final Event<M> START = new Event<>(Event.Type.START);
	protected final Event<M> STOP = new Event<>(Event.Type.STOP);
	protected final Event<M> SEND = new Event<>(Event.Type.SEND);

	protected AbstractFluxer() throws Exception {

		stateSink = Sinks
				.many()
				.multicast().<Change<State>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
		linkSink = Sinks
				.many()
				.multicast().<Change<Link>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
		messageSink = Sinks
				.many()
				.multicast().<Change<Message<M>>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
		logSink = Sinks
				.many()
				.multicast().<Change<Log>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

		// FIXME Use virtual thread?
		stateFlux = stateSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(subscriber -> {
					emitLog(Level.INFO, "A new subscriber is registered to status flux: " + subscriber);
				})
				.log();
		linkFlux = linkSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(subscriber -> {
					emitLog(Level.INFO, "A new subscriber is registered to link flux: " + subscriber);
				})
				.log();
		messageFlux = messageSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(subscriber -> {
					emitLog(Level.INFO, "A new subscriber is registered to message flux: " + subscriber);
				})
				.log();
		logFlux = logSink
				.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.doOnSubscribe(subscriber -> {
					emitLog(Level.INFO, "A new subscriber is registered to log flux: " + subscriber);
				})
				.log();

		emitLog(Level.INFO, "Starting the internal state machine");
		asyncer = createAsyncer();
		asyncer.state()
				.subscribe(stateChange -> {
					emitLog(Level.INFO, "The state of the internal state machine has changed: " + stateChange);
					stateSink.tryEmitNext(stateChange);
				});
	}

	private Asyncer<State, State.Type, Event<M>, Event.Type, Boolean> createAsyncer() {

		var startWhenStopped = new Transition<>("Start when stopped",
				STOPPED, START, STARTING, new ArrayList<>(Arrays.asList(this::processStartRequest)),
				new SequentialFAETaskExecutorImpl<>(), null, STARTED, STOPPED);

		var stopWhenStarted = new Transition<>("Stop when started",
				STARTED, STOP, STOPPING, new ArrayList<>(Arrays.asList(this::processStopRequest)),
				new SequentialFAETaskExecutorImpl<>(), null, STOPPED, STOPPED);

		var sendWhenStarted = new Transition<>("", STARTED, SEND, null,
				new ArrayList<>(Arrays.asList(this::processSendRequest)), new SequentialFAETaskExecutorImpl<>(), null,
				null, null);

		Set<Transition<State, State.Type, Event<M>, Event.Type, Boolean>> transitions = new HashSet<>();
		transitions.add(startWhenStopped);
		transitions.add(stopWhenStarted);
		transitions.add(sendWhenStarted);

		return new DefaultAsyncerImpl<>(STOPPED, null, transitions, new DefaultTransitionExecutorImpl<>());
	}

	@Override
	public UUID uuid() {
		return uuid;
	}

	@Override
	public Flux<Change<State>> state() {
		return stateFlux;
	}

	@Override
	public Flux<Change<Link>> link() {
		return linkFlux;
	}

	@Override
	public Flux<Change<Message<M>>> message() {
		return messageFlux;
	}

	@Override
	public Flux<Change<Log>> log() {
		return logFlux;
	}

	@Override
	public Mono<RequestResult<M>> start() {
		return triggerEvent(START);
	}

	@Override
	public Mono<RequestResult<M>> stop() {
		return triggerEvent(STOP);
	}

	@Override
	public Mono<RequestResult<M>> send(@NonNull M message, @NonNull EndPoint remote) {
		return triggerEvent(new Event<>(Event.Type.SEND, message, remote));
	}

	private Mono<RequestResult<M>> triggerEvent(Event<M> event) {
		final Sinks.One<RequestResult<M>> resultSink = Sinks.one();

		asyncer.fire(AsyncerUtil.generateType1UUID(), event)
				.subscribe(transitionResult -> {
					emitLog(Level.INFO, event + " request processing result: " + transitionResult);
					resultSink.tryEmitValue(new RequestResult<>(transitionResult));
				});

		return resultSink.asMono();
	}

	@Override
	public void close() throws Exception {

		emitLog(Level.INFO, "Sending STOP");
		asyncer.fire(AsyncerUtil.generateType1UUID(), STOP).block();

		emitLog(Level.INFO, "Closing the internal state machine");
		asyncer.close();

		if (stateSink != null) {
			emitLog(Level.INFO, "Closing state flux");
			stateSink.tryEmitComplete();
		}

		if (linkSink != null) {
			emitLog(Level.INFO, "Closing link flux");
			linkSink.tryEmitComplete();
		}

		if (messageSink != null) {
			emitLog(Level.INFO, "Closing message flux");
			messageSink.tryEmitComplete();
		}

		if (logSink != null) {
			emitLog(Level.INFO, "Closing log flux");
			logSink.tryEmitComplete();
		}
	}

	protected abstract Result<Boolean> processStartRequest(State state, Event<M> event);

	protected abstract Result<Boolean> processStopRequest(State state, Event<M> event);

	protected abstract Result<Boolean> processSendRequest(State state, Event<M> event);

	protected Many<Change<Link>> getLinkSink() {
		return linkSink;
	}

	protected Many<Change<Message<M>>> getMessageSink() {
		return messageSink;
	}

	protected Many<Change<Log>> getLogSink() {
		return logSink;
	}

	protected void emitLink(@NonNull Link.Type type, @NonNull String id, @NonNull EndPoint local,
			@NonNull EndPoint remote) {
		linkSink.tryEmitNext(new Change<>(AsyncerUtil.generateType1UUID(), new Link(type, id, local, remote)));
	}

	protected void emitMessage(@NonNull Message.Type type, @NonNull EndPoint local, @NonNull EndPoint remote,
			@NonNull M receivedMessage) {
		messageSink.tryEmitNext(
				new Change<>(AsyncerUtil.generateType1UUID(), new Message<>(type, local, remote, receivedMessage)));
	}

	protected void emitLog(Level level, String description) {
		emitLog(level, description, null);
	}

	protected void emitLog(Level level, String description, Throwable throwable) {
		switch (level) {
			case TRACE:
				log.trace(description);
				break;
			case DEBUG:
				log.debug(description);
				break;
			case INFO:
				log.info(description);
				break;
			case WARN:
				log.warn(description);
				break;
			case ERROR:
				log.error(description);
				break;
			default:
				break;
		}
		logSink.tryEmitNext(new Change<>(AsyncerUtil.generateType1UUID(), new Log(level, description, throwable)));
	}

}