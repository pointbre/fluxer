package com.github.pointbre.fluxer.cli.coach;

public class HappyFortuneService implements FortuneService {

	@Override
	public String getFortune() {
		return "You're lucky today!";
	}	
}
