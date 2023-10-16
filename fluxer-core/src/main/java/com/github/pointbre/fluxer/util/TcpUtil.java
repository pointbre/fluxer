package com.github.pointbre.fluxer.util;

import java.io.IOException;
import java.net.ServerSocket;

public class TcpUtil {
    public static Integer findFreePort() {
	try (ServerSocket serverSocket = new ServerSocket(0)) {
	    serverSocket.setReuseAddress(true);
	    return Integer.valueOf(serverSocket.getLocalPort());
	} catch (IOException e) {
	    throw new IllegalStateException("Could not find a free TCP/IP port");
	}
    }
}
