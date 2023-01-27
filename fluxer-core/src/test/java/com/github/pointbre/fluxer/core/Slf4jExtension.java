package com.github.pointbre.fluxer.core;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jExtension implements TestInstancePostProcessor {

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
		testInstance.getClass()
				.getMethod("setLogger", Logger.class)
				.invoke(testInstance, LoggerFactory.getLogger(testInstance.getClass()));
	}

}
