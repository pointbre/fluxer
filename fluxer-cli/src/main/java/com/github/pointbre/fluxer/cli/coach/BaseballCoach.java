package com.github.pointbre.fluxer.cli.coach;

public class BaseballCoach implements Coach {
	
	// A private field for the dependency
	private FortuneService fortuneService;
	
	// Define a constructor for DI
	public BaseballCoach(FortuneService fortuneService) {
		this.fortuneService = fortuneService;
	}
	
	@Override
	public String getDailyWorkout() {
		return "Spend 30 minutes on batting practice";
	}

	@Override
	public String getDailyFortune() {
		// Use the injected FortuneService
		return fortuneService.getFortune();
	}
	
}
