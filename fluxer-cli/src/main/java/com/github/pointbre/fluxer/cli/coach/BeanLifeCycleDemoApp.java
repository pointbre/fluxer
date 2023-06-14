package com.github.pointbre.fluxer.cli.coach;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class BeanLifeCycleDemoApp {
	public static void main(String[] args) {

		// load the spring configuration file
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("beanLifeCycle-applicationContext.xml");
		
		// retrieve bean from spring container
		Coach coach1 = context.getBean("myCoach", Coach.class);
		
		System.out.println(coach1.getDailyWorkout());
		
		// close the context
		context.close();
	}
}
