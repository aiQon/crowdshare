package de.cased.mobilecloud;

public interface ConnectionStateListener {
	public void notifyAboutConnectionChange();

	public void notifyAboutManagementChange();
}
