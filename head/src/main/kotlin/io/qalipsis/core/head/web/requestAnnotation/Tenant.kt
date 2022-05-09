package io.qalipsis.core.head.web.requestAnnotation

import io.micronaut.core.bind.annotation.Bindable
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
@Bindable
annotation class Tenant

