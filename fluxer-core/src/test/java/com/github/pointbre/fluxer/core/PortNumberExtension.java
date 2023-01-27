package com.github.pointbre.fluxer.core;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

public class PortNumberExtension implements TestInstancePostProcessor {

	private Integer findFreePort() {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			serverSocket.setReuseAddress(true);
			return Integer.valueOf(serverSocket.getLocalPort());
		} catch (IOException e) {
			throw new IllegalStateException("Could not find a free TCP/IP port");
		}
	}

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
		testInstance.getClass()
				.getMethod("setPort", Integer.class)
				.invoke(testInstance, findFreePort());
	}
}
