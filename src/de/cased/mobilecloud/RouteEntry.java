package de.cased.mobilecloud;

public class RouteEntry implements Comparable<RouteEntry>{
	private String destination;
	private String nextHop;
	private int metric;
	public String getDestination() {
		return destination;
	}
	public void setDestination(String destination) {
		this.destination = destination;
	}
	public String getNextHop() {
		return nextHop;
	}
	public void setNextHop(String nextHop) {
		this.nextHop = nextHop;
	}
	public int getMetric() {
		return metric;
	}
	public void setMetric(int metric) {
		this.metric = metric;
	}
	@Override
	public int compareTo(RouteEntry another) {
		return metric - another.getMetric();
	}
	
	
}
