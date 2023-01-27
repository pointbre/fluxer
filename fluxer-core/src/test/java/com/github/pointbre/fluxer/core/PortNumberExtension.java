package com.github.pointbre.fluxer.core;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import com.github.pointbre.fluxer.util.TcpUtil;

public class PortNumberExtension implements TestInstancePostProcessor {

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
		testInstance.getClass()
				.getMethod("setPort", Integer.class)
				.invoke(testInstance, TcpUtil.findFreePort());
	}
}
