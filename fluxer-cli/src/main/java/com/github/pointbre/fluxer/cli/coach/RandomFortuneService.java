package com.github.pointbre.fluxer.cli.coach;

import java.util.Random;

public class RandomFortuneService implements FortuneService {
	private String[] list = new String[] { "Random fortune 1", "Random fortune 2", "Random fortune 3" };

	private Random random = new Random();
	
	@Override
	public String getFortune() {
		return list[random.nextInt(list.length)];
	}

}
