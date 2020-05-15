package io.evolue.api.java.annotations;

import io.evolue.api.java.components.TestDataSource;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a class describing a data source or a method instantiating one. The component has to implement {@link
 * TestDataSource}.
 *
 * @see Data
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@Documented
public @interface DataSourceDescriptor {

	boolean singleton() default true;

	/**
	 * Name of the data source, in order to reference it for use. You can set several names for aliasing. If no value is
	 * supplied, the name will be created from the annotated class or method.
	 */
	String[] value() default "";

	/**
	 * Defines if the datasource has to be reset when it comes to the end.
	 */
	boolean loopOver() default false;
}
