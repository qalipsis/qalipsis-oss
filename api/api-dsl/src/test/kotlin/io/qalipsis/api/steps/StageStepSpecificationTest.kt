package io.qalipsis.api.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.time.Duration


internal class StageStepSpecificationTest {

    @Test
    internal fun `should add a minimal stage as next and transport the right input types`() {
        val previousStep = DummyStepSpecification()
        val nextStep = previousStep.stage {
            captureInputAndReturn(String::class).captureInputAndReturn(Double::class)
        }.configure { name = "no-name" }.captureInputAndReturn(Unit::class)

        // Even if the step specifications are added in the group from a DSL perspective, they are actually linked to each other.
        assertThat(previousStep.nextSteps[0]).isInstanceOf(StageStepStartSpecification::class).all {
            prop(StageStepStartSpecification<*>::name).isEqualTo("no-name")
            prop(StageStepStartSpecification<*>::retryPolicy).isNull()
            prop(StageStepStartSpecification<*>::iterations).isEqualTo(1)
            prop(StageStepStartSpecification<*>::iterationPeriods).isEqualTo(Duration.ZERO)

            prop(StageStepStartSpecification<*>::nextSteps).all {
                hasSize(1)
                // First step of the stage.
                index(0).isInstanceOf(InputTypeCaptor::class).all {
                    prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(Int::class)
                    prop(InputTypeCaptor<*, *>::nextSteps).all {
                        hasSize(1)
                        // Second step of the stage.
                        index(0).isInstanceOf(InputTypeCaptor::class).all {
                            prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(String::class)
                            prop(InputTypeCaptor<*, *>::nextSteps).all {
                                hasSize(1)
                                // End of the stage.
                                index(0).isInstanceOf(StageStepEndSpecification::class).all {
                                    prop(StageStepEndSpecification<*, *>::nextSteps).all {
                                        hasSize(1)
                                        // Step after the stage.
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
    internal fun `should add a configured stage as next and transport the right input types`() {
        val previousStep = DummyStepSpecification()
        val retryPolicy: RetryPolicy = relaxedMockk()
        val nextStep = previousStep.stage("my-stage") {
            captureInputAndReturn(String::class).captureInputAndReturn(Double::class)
        }.configure {
            retry(retryPolicy)
            iterate(123, Duration.ofMinutes(1))
        }.captureInputAndReturn(Unit::class)

        // Even if the step specifications are added in the group from a DSL perspective, they are actually linked to each other.
        assertThat(previousStep.nextSteps[0]).isInstanceOf(StageStepStartSpecification::class).all {
            prop(StageStepStartSpecification<*>::name).isEqualTo("my-stage")
            prop(StageStepStartSpecification<*>::retryPolicy).isSameAs(retryPolicy)
            prop(StageStepStartSpecification<*>::iterations).isEqualTo(123)
            prop(StageStepStartSpecification<*>::iterationPeriods).isEqualTo(Duration.ofMinutes(1))

            prop(StageStepStartSpecification<*>::nextSteps).all {
                hasSize(1)
                // First step of the stage.
                index(0).isInstanceOf(InputTypeCaptor::class).all {
                    prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(Int::class)
                    prop(InputTypeCaptor<*, *>::nextSteps).all {
                        hasSize(1)
                        // Second step of the stage.
                        index(0).isInstanceOf(InputTypeCaptor::class).all {
                            prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(String::class)
                            prop(InputTypeCaptor<*, *>::nextSteps).all {
                                hasSize(1)
                                // End of the stage.
                                index(0).isInstanceOf(StageStepEndSpecification::class).all {
                                    prop(StageStepEndSpecification<*, *>::nextSteps).all {
                                        hasSize(1)
                                        // Step after the stage.
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
    internal fun `should add a minimal stage to the scenario and transport the right input types`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        val nextStep = scenario.stage {
            returns("").captureInputAndReturn(Double::class)
        }.configure { name = "no-name" }.captureInputAndReturn(Unit::class)

        // Even if the step specifications are added in the group from a DSL perspective, they are actually linked to each other.
        assertThat(scenario.rootSteps).all {
            hasSize(1)
            index(0).isInstanceOf(StageStepStartSpecification::class).all {
                prop(StageStepStartSpecification<*>::name).isEqualTo("no-name")
                prop(StageStepStartSpecification<*>::retryPolicy).isNull()
                prop(StageStepStartSpecification<*>::iterations).isEqualTo(1)
                prop(StageStepStartSpecification<*>::iterationPeriods).isEqualTo(Duration.ZERO)

                prop(StageStepStartSpecification<*>::nextSteps).all {
                    hasSize(1)
                    // First step of the stage.
                    index(0).isInstanceOf(SimpleStepSpecification::class).all {
                        prop(SimpleStepSpecification<*, *>::nextSteps).all {
                            hasSize(1)
                            // Second step of the stage.
                            index(0).isInstanceOf(InputTypeCaptor::class).all {
                                prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(String::class)
                                prop(InputTypeCaptor<*, *>::nextSteps).all {
                                    hasSize(1)
                                    // End of the stage.
                                    index(0).isInstanceOf(StageStepEndSpecification::class).all {
                                        prop(StageStepEndSpecification<*, *>::nextSteps).all {
                                            hasSize(1)
                                            // Step after the stage.
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

    @Test
    internal fun `should add a configured stage to the scenario with existing steps and transport the right input types`() {
        val retryPolicy: RetryPolicy = relaxedMockk()
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.returns(1).configure { name = "step-1" }
        scenario.returns(2).configure { name = "step-2" }

        val nextStep = scenario.stage("my-stage") {
            returns("").configure { name = "step-3" }
                .captureInputAndReturn(Double::class)
        }.configure {
            retry(retryPolicy)
            iterate(123, Duration.ofMinutes(1))
        }.captureInputAndReturn(Unit::class)

        // Even if the step specifications are added in the group from a DSL perspective, they are actually linked to each other.
        assertThat(scenario.rootSteps).all {
            hasSize(3)
            index(0).isInstanceOf(SimpleStepSpecification::class).prop(SimpleStepSpecification<*, *>::name)
                .isEqualTo("step-1")
            index(1).isInstanceOf(SimpleStepSpecification::class).prop(SimpleStepSpecification<*, *>::name)
                .isEqualTo("step-2")
            index(2).isInstanceOf(StageStepStartSpecification::class).all {
                prop(StageStepStartSpecification<*>::name).isEqualTo("my-stage")
                prop(StageStepStartSpecification<*>::retryPolicy).isSameAs(retryPolicy)
                prop(StageStepStartSpecification<*>::iterations).isEqualTo(123)
                prop(StageStepStartSpecification<*>::iterationPeriods).isEqualTo(Duration.ofMinutes(1))

                prop(StageStepStartSpecification<*>::nextSteps).all {
                    hasSize(1)
                    // First step of the stage.
                    index(0).isInstanceOf(SimpleStepSpecification::class).all {
                        prop(SimpleStepSpecification<*, *>::name).isEqualTo("step-3")
                        prop(SimpleStepSpecification<*, *>::nextSteps).all {
                            hasSize(1)
                            // Second step of the stage.
                            index(0).isInstanceOf(InputTypeCaptor::class).all {
                                prop(InputTypeCaptor<*, *>::inputClass).isEqualTo(String::class)
                                prop(InputTypeCaptor<*, *>::nextSteps).all {
                                    hasSize(1)
                                    // End of the stage.
                                    index(0).isInstanceOf(StageStepEndSpecification::class).all {
                                        prop(StageStepEndSpecification<*, *>::nextSteps).all {
                                            hasSize(1)
                                            // Step after the stage.
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
}
