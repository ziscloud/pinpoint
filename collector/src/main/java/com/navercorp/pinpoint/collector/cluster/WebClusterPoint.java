package com.nhn.pinpoint.collector.cluster;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhn.pinpoint.rpc.PinpointSocketException;
import com.nhn.pinpoint.rpc.client.MessageListener;
import com.nhn.pinpoint.rpc.client.PinpointSocket;
import com.nhn.pinpoint.rpc.client.PinpointSocketFactory;

/**
 * @author koo.taejin <kr14910>
 */
public class WebClusterPoint implements ClusterPoint {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final PinpointSocketFactory factory;

	private final Map<InetSocketAddress, PinpointSocket> clusterRepository = new HashMap<InetSocketAddress, PinpointSocket>();

	public WebClusterPoint(String id, MessageListener messageListener) {
		this.factory = new PinpointSocketFactory();
		this.factory.setTimeoutMillis(1000 * 5);
		this.factory.setMessageListener(messageListener);

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("id", id);
		
		factory.setProperties(properties);
	}

	// Not safe for use by multiple threads.
	public void connectPointIfAbsent(InetSocketAddress address) {
		logger.info("localhost -> {} connect started.", address);
		
		if (clusterRepository.containsKey(address)) {
			logger.info("localhost -> {} already connected.", address);
			return;
		}
		
		PinpointSocket socket = createPinpointSocket(address);
		clusterRepository.put(address, socket);
		
		logger.info("localhost -> {} connect completed.", address);
	}

	// Not safe for use by multiple threads.
	public void disconnectPoint(InetSocketAddress address) {
		logger.info("localhost -> {} disconnect started.", address);

		PinpointSocket socket = clusterRepository.remove(address);
		if (socket != null) {
			socket.close();
			logger.info("localhost -> {} disconnect completed.", address);
		} else {
			logger.info("localhost -> {} already disconnected.", address);
		}
	}

	private PinpointSocket createPinpointSocket(InetSocketAddress address) {
		String host = address.getHostName();
		int port = address.getPort();

		PinpointSocket socket = null;
		for (int i = 0; i < 3; i++) {
			try {
				socket = factory.connect(host, port);
				logger.info("tcp connect success:{}/{}", host, port);
				return socket;
			} catch (PinpointSocketException e) {
				logger.warn("tcp connect fail:{}/{} try reconnect, retryCount:{}", host, port, i);
			}
		}
		logger.warn("change background tcp connect mode  {}/{} ", host, port);
		socket = factory.scheduledConnect(host, port);

		return socket;
	}

	public List<InetSocketAddress> getWebClusterList() {
		return new ArrayList<InetSocketAddress>(clusterRepository.keySet());
	}
	
	public void close() {
		for (PinpointSocket socket : clusterRepository.values()) {
			if (socket != null) {
				socket.close();
			}
		}
		
		if (factory != null) {
			factory.release();
		}
	}

}