package io.evolue.api.java.annotations;

import io.evolue.api.java.components.TestDataSource;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a serializer of data data.
 *
 * @see TestDataSource
 * @see DataSourceDescriptor
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
@Documented
public @interface ConverterDescriptor {

}
