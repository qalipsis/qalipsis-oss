package io.evolue.api.java.components;

public interface PaceStrategy {

	/**
	 * Blocks until it is time to run the next action.
	 */
	void next();
}
