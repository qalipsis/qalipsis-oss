package io.evolue.api.java.annotations;

import io.evolue.api.java.components.TestDataSource;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a parameter to describe the value coming from a data source either for a test or an assertion.
 *
 * @see TestDataSource
 * @see DataSourceDescriptor
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@Inherited
@Documented
public @interface Data {

	/**
	 * Name of the datasource providing the data.
	 */
	String value();

}
