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
import com.github.pointbre.asyncer.core.Asyncer.TaskResult;
import com.github.pointbre.asyncer.core.Asyncer.Transition;
import com.github.pointbre.asyncer.core.Asyncer.TransitionExecutor;
import com.github.pointbre.asyncer.core.AsyncerUtil;
import com.github.pointbre.asyncer.core.DefaultAsyncerImpl;
import com.github.pointbre.asyncer.core.DefaultTransitionExecutorImpl;
import com.github.pointbre.asyncer.core.SequentialFAETaskExecutor;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

@Slf4j
public abstract class AbstractFluxer<T> implements Fluxer<T> {

	private final UUID uuid = AsyncerUtil.generateType1UUID();

	private Many<State> stateSink;
	private Many<Link> linkSink;
	private Many<Message<T>> messageSink;
	private Many<Log> logSink;

	private Flux<State> stateFlux;
	private Flux<Link> linkFlux;
	private Flux<Message<T>> messageFlux;
	private Flux<Log> logFlux;

	protected Asyncer<State.Type, State.Event, Boolean> asyncer;

	protected AbstractFluxer() throws Exception {

		stateSink = Sinks
				.many()
				.multicast().<State>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
		linkSink = Sinks
				.many()
				.multicast().<Link>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
		messageSink = Sinks
				.many()
				.multicast().<Message<T>>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
		logSink = Sinks
				.many()
				.multicast().<Log>onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

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

		asyncer.stateChange()
				.subscribe(state -> {
					emitLog(Level.INFO, "The state of the internal state machine has changed: " + state);
					stateSink.tryEmitNext(new State(asyncer.uuid(), state.getState(), null));
				});
	}

	@Override
	public UUID uuid() {
		return uuid;
	}

	@Override
	public Flux<State> state() {
		return stateFlux;
	}

	@Override
	public Flux<Link> link() {
		return linkFlux;
	}

	@Override
	public Flux<Message<T>> message() {
		return messageFlux;
	}

	@Override
	public Flux<Log> log() {
		return logFlux;
	}

	@Override
	public void close() throws Exception {

		processStopRequest();

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

	protected abstract TaskResult<Boolean> processStartRequest();

	protected abstract TaskResult<Boolean> processStopRequest();

	// protected abstract TaskResult<Boolean> processSendRequest();

	protected Many<Link> getLinkSink() {
		return linkSink;
	}

	protected Many<Message<T>> getMessageSink() {
		return messageSink;
	}

	protected Many<Log> getLogSink() {
		return logSink;
	}

	protected void emitLink(String id, Link.State state, EndPoint local, EndPoint remote) {
		getLinkSink().tryEmitNext(new Link(id, state, local, remote));
	}

	protected void emitMessage(Message.Type type, EndPoint local, EndPoint remote,
			T receivedMessage) {
		getMessageSink().tryEmitNext(Message.<T>of(type, local, remote, receivedMessage));
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
		getLogSink().tryEmitNext(new Log(level, description, throwable));
	}

	protected void emitLog(Level level, String description) {
		emitLog(level, description, null);
	}

	private Asyncer<State.Type, State.Event, Boolean> createAsyncer() {

		var startWhenStopped = new Transition<State.Type, State.Event, Boolean>("", State.Type.STOPPED,
				State.Event.START, State.Type.STARTING, new ArrayList<>(Arrays.asList(
						this::processStartRequest)),
				SequentialFAETaskExecutor.class, null,
				State.Type.STARTED, State.Type.STOPPED);

		var stopWhenStarted = new Transition<State.Type, State.Event, Boolean>("", State.Type.STARTED,
				State.Event.STOP, State.Type.STOPPING, new ArrayList<>(Arrays.asList(
						this::processStopRequest)),
				SequentialFAETaskExecutor.class, null,
				State.Type.STOPPED, State.Type.STOPPED);

		// var sendWhenStarted = new Transition<State.Type, State.Event, Boolean>("",
		// State.Type.STARTED, State.Event.SEND,
		// null, new ArrayList<>(Arrays.asList(this::processSendRequest)),
		// SequentialFAETaskExecutor.class, null,
		// null, null);

		Set<Transition<State.Type, State.Event, Boolean>> transitions = new HashSet<>();
		transitions.add(startWhenStopped);
		transitions.add(stopWhenStarted);
		// transitions.add(sendWhenStarted);

		TransitionExecutor<State.Type, State.Event, Boolean> transitionExecutor = new DefaultTransitionExecutorImpl<>();

		return new DefaultAsyncerImpl<>(State.Type.STOPPED, null, transitions, transitionExecutor);
	}

}