package com.github.pointbre.fluxer.cli.coach;

import org.springframework.stereotype.Component;

@Component
public class TrackCoach implements Coach {
	
	private FortuneService fortuneService;
	
	public TrackCoach(FortuneService fortuneService) {
		this.fortuneService = fortuneService;
	}
	
	public TrackCoach() {
		super();
	}
	
//	private void onInit() {
//		System.out.println("onInit");
//	}
//	
//	private void onDestroy() {
//		System.out.println("onDestroy");
//	}


	@Override
	public String getDailyWorkout() {
		return "Run a hard 5K faster";
	}

	@Override
	public String getDailyFortune() {
		return "Just do it! " + fortuneService.getFortune();
	}

}
