package io.evolue.api.java.components;

public interface RampUpStrategy {

	/**
	 * Returns the number of items to ramp-up to activate. This call is blocking until it is actually time to start
	 * those items.
	 */
	int next();
}
