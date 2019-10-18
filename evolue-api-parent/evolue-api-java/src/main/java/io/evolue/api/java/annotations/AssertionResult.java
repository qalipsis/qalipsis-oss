package io.evolue.api.java.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a parameter which should be fed with the data provided to the assertion.
 *
 * @see io.evolue.api.java.components.AssertionDataSource
 * @see Assertion
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@Inherited
@Documented
public @interface AssertionResult {

}
