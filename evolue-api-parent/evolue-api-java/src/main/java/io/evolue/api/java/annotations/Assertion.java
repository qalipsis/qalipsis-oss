package io.evolue.api.java.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for methods describing an assertion to execute.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Inherited
@Documented
public @interface Assertion {

	/**
	 * Name of assertion. If no value is supplied, the name will be created from the annotated class and its parent
	 * scenario.
	 */
	String value() default "";

	/**
	 * Selectors to determine the agents on which the assertion has to be executed.
	 */
	String[] selectors() default {};

	/**
	 * Timeout after which the assertion is cancelled and considered failed.
	 */
	int timeout() default 0;

	/**
	 * Unit used for the time out value.
	 */
	TimeUnit timeoutUnit() default TimeUnit.MILLISECONDS;

	/**
	 * Name of the tests that have to be asserted.
	 *
	 * @see Action#value
	 */
	String of();

	/**
	 * Number of successive technical failure allowed before the assertion is interrupted.
	 */
	int failureBeforeInterrupt() default 3;

	/**
	 * Expression to process to extract the key to map to the data received from the source to the assertion.
	 */
	String dataKeyExpression() default "";

	/**
	 * Expression to process to extract the key from the test data or returned value.
	 */
	String testKeyExpression() default "";

}
