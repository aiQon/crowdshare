package de.cased.mobilecloud;

public class FacebookEntry {

	private String name;
	private String id;

	public FacebookEntry(String name, String id) {
		this.name = name;
		this.id = id;
	}

	public FacebookEntry(String cvs) {
		id = cvs.substring(0, cvs.indexOf(':'));
		name = cvs.substring(cvs.indexOf(':') + 1);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return id + ":" + name;
	}

}
