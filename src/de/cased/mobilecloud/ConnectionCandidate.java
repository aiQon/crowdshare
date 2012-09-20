package de.cased.mobilecloud;

public class ConnectionCandidate {
	// private String destination;
	private long lastConnectionAttempt = 0L;
	private int connectionAttempts = 0;

	// public String getDestination() {
	// return destination;
	// }
	// public void setDestination(String destination) {
	// this.destination = destination;
	// }
	public long getLastConnectionAttempt() {
		return lastConnectionAttempt;
	}
	public void setLastConnectionAttempt(long lastConnectionAttempt) {
		this.lastConnectionAttempt = lastConnectionAttempt;
	}

	public int getConnectionAttempts() {
		return connectionAttempts;
	}

	public void setConnectionAttempts(int connectionAttempts) {
		this.connectionAttempts = connectionAttempts;
	}

	public void increaseConnectionAttempts() {
		connectionAttempts++;
	}
}
