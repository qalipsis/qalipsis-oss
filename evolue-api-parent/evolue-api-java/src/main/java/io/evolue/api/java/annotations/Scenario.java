package io.evolue.api.java.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a class describing an Evolue scenario. It can also been used on a method returning a scenario class
 * instance.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@Documented
public @interface Scenario {

	/**
	 * Name of scenario. If no value is supplied, the name will be created from the annotated component.
	 */
	String value() default "";

	/**
	 * Default count of the machines running the scenario.
	 */
	int machines() default 1;

	/**
	 * When set to true, the machine running a test will be interrupted if a test execution is interrupted before
	 * expected end.
	 *
	 * @see TestDescriptor#failureBeforeInterrupt
	 */
	boolean interruptOnTestFailure() default false;

	/**
	 * When set to true, the machine running a test will be interrupted if an assertion execution is interrupted before
	 * expected end.
	 *
	 * @see AssertionDescriptor#failureBeforeInterrupt
	 */
	boolean interruptOnAssertionFailure() default false;

	/**
	 * Selectors to determine the agents on which the scenario have to be executed.
	 */
	String[] selectors() default {};

	/**
	 * Tags to add to the metrics and results.
	 */
	String[] tags() default {};
}
