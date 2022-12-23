package com.github.pointbre.fluxer.cli;

import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class FluxerRestController {
	
	@Autowired
	private final LinkService linkService;
	
	public FluxerRestController(LinkService linkService) {
		this.linkService = linkService;
	}
	
	// dto object for host, port, options...

	@GetMapping("/fluxer/start")
	public Mono<Void> start() {
		return linkService.start().onErrorComplete();
	}

	@GetMapping("/fluxer/stop")
	public Mono<Void> stop() {
		return linkService.stop();
	}
	
	@GetMapping("/fluxer/write")
	public Mono<Void> write() {
		return linkService.write(LocalTime.now().toString().getBytes());
	}
}
