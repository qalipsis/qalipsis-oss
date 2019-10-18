package io.evolue.api.java.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods describing a test to execute.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Inherited
@Documented
public @interface Action {

	/**
	 * Name of test. If no value is supplied, the name will be created from the annotated class and its parent
	 * scenario.
	 */
	String value() default "";

	/**
	 * Selectors to determine the agents on which the test has to be executed.
	 */
	String[] selectors() default {};

	/**
	 * Number of successive iterations. Values strictly lower than 1 are considered infinite.
	 */
	int iterations() default 1;

	/**
	 * Name or alias of the pace strategy component to define the iterations pace. Not relevant if iterations is equal
	 * to 1. A blank or empty value means that the iterations will be executed with no delay in the between.
	 *
	 * @see PaceStrategyDescriptor#value
	 */
	String paceStrategy() default "";

	/**
	 * Name or alias of the retry strategy component to define the behavior in case of a technical failure during the
	 * test. A blank or empty value means that test are not retried.
	 *
	 * @see RetryStrategyDescriptor#value
	 */
	String retryStrategy() default "";

	/**
	 * Number of successive technical failure allowed before the test is interrupted.
	 */
	int failureBeforeInterrupt() default 3;

	/**
	 * Each iteration of the test will be run after each successful execution of the given test or assertion. The field
	 * iterations is taken into account as a maximal possible executions.
	 */
	String afterEachSuccess() default "";

	/**
	 * Trigger of the test if it has to follow an assertion or a test unique execution, whatever the execution result
	 * is.
	 */
	String afterEachAlways() default "";

	/**
	 * Trigger of the test if it has to follow an assertion or a test unique execution without no failure
	 */
	String afterCompletedSuccess() default "";

	/**
	 * Trigger of the test if it has to follow an assertion or a test unique execution, whatever the execution result
	 * is.
	 */
	String afterCompletedAlways() default "";
}
