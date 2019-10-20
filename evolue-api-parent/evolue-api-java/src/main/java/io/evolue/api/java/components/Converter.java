package io.evolue.api.java.components;

/**
 * Bi-directional converter from a type to another. generally used for serializing and deserializing.
 * @param <U>
 * @param <V>
 */
public interface Converter<U, V> {

	V to(U object);

	U from(V object);
}
