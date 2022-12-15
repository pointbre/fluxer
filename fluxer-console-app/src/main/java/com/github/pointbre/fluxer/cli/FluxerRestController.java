package com.github.pointbre.fluxer.cli;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class FluxerRestController {
	
	@Autowired
	private final LinkService linkService;
	
	public FluxerRestController(LinkService linkService) {
		this.linkService = linkService;
	}

	@GetMapping("/fluxer/start")
	public Mono<Void> start() {
		return linkService.start().onErrorComplete();
	}

	@GetMapping("/fluxer/stop")
	public Mono<Void> stop() {
		return linkService.stop();
	}
	
	@GetMapping("/fluxer/initialize")
	public Mono<Void> initialize() {
		return linkService.initialize();
	}
	
	@GetMapping("/fluxer/destroy")
	public Mono<Void> destroy() {
		return linkService.destroy();
	}
}
