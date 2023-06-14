package com.github.pointbre.fluxer.cli.annotation;

import org.springframework.stereotype.Component;

@Component()
//@Component("thatSillyCoach")
public class TennisCoach implements Coach {

	@Override
	public String getDailyWorkout() {
		
		return "Practice your backhand stroke!";
	}

}
