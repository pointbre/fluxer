package com.github.pointbre.fluxer.cli.coach;

import lombok.extern.slf4j.Slf4j;

//@SpringBootApplication
@Slf4j
public class App {

	public static void main(String[] args) {
		
		// create the object
		Coach theCoach = new TrackCoach();				
		
		// use the object
		System.out.println(theCoach.getDailyWorkout());
	}
}