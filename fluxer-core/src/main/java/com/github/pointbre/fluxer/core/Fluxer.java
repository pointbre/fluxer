package com.github.pointbre.fluxer.core;

import com.github.pointbre.fluxer.core.Fluxer.Link;
import com.github.pointbre.fluxer.core.Fluxer.Message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Fluxer {

	Mono<Void> start();

	Mono<Void> stop();

	Flux<Status> status();

	Flux<Link> link();
	
    Flux<Message> read();
    
    Mono<Void> write(Message message);

	public enum Status {

		STARTING, STARTED, STOPPING, STOPPED;

	}

	@Value
	@AllArgsConstructor
	@Getter
	@ToString
	public class Link {

		@NonNull
		private String localIPAddress;

		@NonNull
		private Integer localPort;

		@NonNull
		private String remoteIPAddress;

		@NonNull
		private Integer remotePort;

		@NonNull
		private Status status;

		public enum Status {

			CONNECTED, DISCONNECTED, NONE;

		}
		
	}

	@Value
	@AllArgsConstructor
	@Getter
	@ToString
	public class Message {

		@NonNull
		private Link link;

		@NonNull
		private byte[] message;

	}

	public class FluxerException extends Exception {

		public FluxerException(String message) {
			super(message);
		}

	}

	public class StartException extends FluxerException {

		public StartException(String message) {
			super(message);
		}

	}

	public class StopException extends FluxerException {

		public StopException(String message) {
			super(message);
		}

	}

	public class WriteException extends FluxerException {

		public WriteException(String message) {
			super(message);
		}

	}

}
