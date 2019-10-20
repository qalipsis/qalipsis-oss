package io.evolue.api.java.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a class describing a ramp-up strategy or a method instantiating one. The component has to implement
 * {@link io.evolue.api.java.components.RampUpStrategy}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@Documented
public @interface RampUpStrategyDescriptor {

	boolean singleton() default true;

	/**
	 * Name of the ramp-up strategy, in order to reference it for use. You can set several names for aliasing. If no value
	 * is supplied, the name will be created from the annotated class or method.
	 */
	String[] value() default "";

}
