package com.github.pointbre.fluxer.cli.coach;

public class CricketCoach implements Coach {
	
	private FortuneService fortuneService;
	
	private String emailAddress;
	
	private String team;
	
	public CricketCoach() {
		System.out.println("CricketCoach: no-arg constructor");
	}
	
	public void setFortuneService(FortuneService fortuneService) {
		System.out.println("CricketCoach: setFortuneService()");
		this.fortuneService = fortuneService;
	}

	@Override
	public String getDailyWorkout() {
		return "Do something!";
	}

	@Override
	public String getDailyFortune() {
		return fortuneService.getFortune();
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		System.out.println("CricketCoach: setEmailAddress -> " + emailAddress);
		this.emailAddress = emailAddress;
	}

	public String getTeam() {
		return team;
	}

	public void setTeam(String team) {
		System.out.println("CricketCoach: setTeam -> " + team);
		this.team = team;
	}

	
}
