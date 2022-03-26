package io.qalipsis.core.directives

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("testSingleUseDirective")
data class TestSingleUseDirective(
    val value: Int = 1,
    override val channel: DispatcherChannel = ""
) : SingleUseDirective<TestSingleUseDirectiveReference>() {

    override fun toReference(key: DirectiveKey): TestSingleUseDirectiveReference {
        return TestSingleUseDirectiveReference(key)
    }
}

@Serializable
@SerialName("testSingleUseDirectiveReference")
data class TestSingleUseDirectiveReference(
    override val key: DirectiveKey
) : SingleUseDirectiveReference()

@Serializable
@SerialName("testDescriptiveDirective")
data class TestDescriptiveDirective(
    val value: Int = 1,
    override val channel: DispatcherChannel = ""
) : DescriptiveDirective()
