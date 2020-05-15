package io.evolue.api.java.components;

public interface TestDataSource<T> {

	default void reset() {
		// NO-OP.
	}

	boolean hasNext();

	T next();
}
