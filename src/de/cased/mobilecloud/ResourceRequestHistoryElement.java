package de.cased.mobilecloud;

import java.util.Date;

public class ResourceRequestHistoryElement {
	private String ip;
	private String layer4;
	private int port;
	private Date timestamp;

	public ResourceRequestHistoryElement(String ip, String layer4, int port,
			Date timestamp) {
		this.ip = ip;
		this.layer4 = layer4;
		this.port = port;
		this.timestamp = timestamp;
	}

	public String getIp() {
		return ip;
	}

	public String getLayer4() {
		return layer4;
	}

	public int getPort() {
		return port;
	}

	public Date getTimestamp() {
		return timestamp;
	}

}
