package com.github.pointbre.fluxer.cli.coach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

	private Coach myCoach;
	
	@Autowired
	// Constructor injection
//	public DemoController(Coach theCoach) { this.myCoach = theCoach; }
	// Setter injection
	public void setCoach(@Qualifier("trackCoach") Coach theCoach) {
		this.myCoach = theCoach;
	}
	
	@GetMapping("/dailyworkout")
	public String getDailyWorkout() {
		return myCoach.getDailyWorkout();
	}
}
