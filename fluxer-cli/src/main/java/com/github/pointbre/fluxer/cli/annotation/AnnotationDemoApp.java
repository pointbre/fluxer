package com.github.pointbre.fluxer.cli.annotation;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AnnotationDemoApp {
	public static void main(String[] args) {

		// load the spring configuration file
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("annotation-applicationContext.xml");
		
//		Coach coach = context.getBean("tennisCoach", Coach.class); // Using default bean ID
		Coach coach = context.getBean("thatSillyCoach", Coach.class);
		
		System.out.println(coach.getDailyWorkout());
				
		// close the context
		context.close();
	}
}
