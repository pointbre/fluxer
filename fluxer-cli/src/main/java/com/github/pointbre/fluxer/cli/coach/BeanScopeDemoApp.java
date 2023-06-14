package com.github.pointbre.fluxer.cli.coach;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class BeanScopeDemoApp {
	public static void main(String[] args) {

		// load the spring configuration file
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("beanScope-applicationContext.xml");
		
		// retrieve bean from spring container
		Coach coach1 = context.getBean("myCoach", Coach.class);
		Coach coach2 = context.getBean("myCoach", Coach.class);
		
		System.out.println(coach1 == coach2);
		System.out.println("coach1 memory location:" + coach1);
		System.out.println("coach2 memory location:" + coach2);
		
		// close the context
		context.close();
	}
}
