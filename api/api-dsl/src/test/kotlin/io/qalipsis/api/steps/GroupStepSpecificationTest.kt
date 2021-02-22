package io.qalipsis.api.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.time.Duration


internal class GroupStepSpecificationTest {

    @Test
    internal fun `should add a minimal group of steps as next and transport the right input types`() {
        val previousStep = DummyStepSpecification()
        val nextStep = previousStep.group {
            captureInputAndReturn(String::class).captureInputAndReturn(Double::class)
        }.captureInputAndReturn(Unit::class)

        // Even if the step specifications are added in the group from a DSL perspective,

        assertThat(previousStep.nextSteps[0]).isInstanceOf(GroupStepStartSpecification::class).all {
            prop(GroupStepStartSpecification<*>::retryPolicy).isNull()
            prop(GroupStepStartSpecification<*>::iterations).isEqualTo(1)
            prop(GroupStepStartSpecification<*>::iterationPeriods).isEqualTo(Duration.ZERO)

            prop(GroupStepStartSpecification<*>::nextSteps).all {
                hasSize(1)
                index(0).isInstanceOf(InputTypeCaptor::class).all {
                    prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(Int::class)

                    prop(InputTypeCaptor<*, *>::nextSteps).all {
                        hasSize(1)
                        index(0).isInstanceOf(InputTypeCaptor::class).all {
                            prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(String::class)

                            prop(InputTypeCaptor<*, *>::nextSteps).all {
                                hasSize(1)

                                index(0).isInstanceOf(GroupStepEndSpecification::class).all {
                                    prop(GroupStepEndSpecification<*, *>::nextSteps).all {
                                        hasSize(1)
                                        index(0).isInstanceOf(InputTypeCaptor::class).all {
                                            prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(Double::class)
                                            isSameAs(nextStep)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    internal fun `should add a configured group of steps as next and transport the right input types`() {
        val previousStep = DummyStepSpecification()
        val retryPolicy: RetryPolicy = relaxedMockk()
        val nextStep = previousStep.group {
            captureInputAndReturn(String::class).captureInputAndReturn(Double::class)
        }.configure {
            retry(retryPolicy)
            iterate(123, Duration.ofMinutes(1))
        }.captureInputAndReturn(Unit::class)

        // Even if the step specifications are added in the group from a DSL perspective,

        assertThat(previousStep.nextSteps[0]).isInstanceOf(GroupStepStartSpecification::class).all {
            prop(GroupStepStartSpecification<*>::retryPolicy).isSameAs(retryPolicy)
            prop(GroupStepStartSpecification<*>::iterations).isEqualTo(123)
            prop(GroupStepStartSpecification<*>::iterationPeriods).isEqualTo(Duration.ofMinutes(1))

            prop(GroupStepStartSpecification<*>::nextSteps).all {
                hasSize(1)
                index(0).isInstanceOf(InputTypeCaptor::class).all {
                    prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(Int::class)

                    prop(InputTypeCaptor<*, *>::nextSteps).all {
                        hasSize(1)
                        index(0).isInstanceOf(InputTypeCaptor::class).all {
                            prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(String::class)

                            prop(InputTypeCaptor<*, *>::nextSteps).all {
                                hasSize(1)

                                index(0).isInstanceOf(GroupStepEndSpecification::class).all {
                                    prop(GroupStepEndSpecification<*, *>::nextSteps).all {
                                        hasSize(1)
                                        index(0).isInstanceOf(InputTypeCaptor::class).all {
                                            prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(Double::class)
                                            isSameAs(nextStep)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
