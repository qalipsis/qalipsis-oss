package io.qalipsis.core.directives

import io.qalipsis.api.dev.CuidBasedIdGenerator
import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DispatcherChannel
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.ListDirectiveReference
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.QueueDirectiveReference
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirectiveReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("testQueueDirective")
class TestQueueDirective(override val values: List<Int>, override val key: DirectiveKey = CuidBasedIdGenerator().long(), override val channel: DispatcherChannel = "broadcast-channel") : QueueDirective<Int, QueueDirectiveReference<Int>>() {

    private val ref = TestQueueDirectiveReference("my-queue-directive")

    override fun toReference(): QueueDirectiveReference<Int> {
        return ref
    }
}
@Serializable
@SerialName("testQueueDirectiveReference")
class TestQueueDirectiveReference(override val key: DirectiveKey, override val channel: DispatcherChannel = "broadcast-channel") : QueueDirectiveReference<Int>()

@Serializable
@SerialName("testListDirective")
class TestListDirective(override val values: List<Int>, override val key: DirectiveKey = CuidBasedIdGenerator().long(), override val channel: DispatcherChannel = "broadcast-channel") : ListDirective<Int, ListDirectiveReference<Int>>() {

    private val ref = TestListDirectiveReference("my-list-directive")

    override fun toReference(): ListDirectiveReference<Int> {
        return ref
    }
}

@Serializable
@SerialName("testListDirectiveReference")
class TestListDirectiveReference(override val key: DirectiveKey, override val channel: DispatcherChannel = "broadcast-channel") : ListDirectiveReference<Int>()

@Serializable
@SerialName("testSingleUseDirective")
class TestSingleUseDirective(override val value: Int, override val key: DirectiveKey = CuidBasedIdGenerator().long(), override val channel: DispatcherChannel = "broadcast-channel") : SingleUseDirective<Int, SingleUseDirectiveReference<Int>>() {

    private val ref = TestSingleUseDirectiveReference("my-single-use-directive")

    override fun toReference(): SingleUseDirectiveReference<Int> {
        return ref
    }
}

@Serializable
@SerialName("testSingleUseDirectiveReference")
class TestSingleUseDirectiveReference(override val key: DirectiveKey, override val channel: DispatcherChannel = "broadcast-channel") : SingleUseDirectiveReference<Int>()

@Serializable
@SerialName("testDescriptiveDirective")
class TestDescriptiveDirective(override val key: DirectiveKey = CuidBasedIdGenerator().long(), override val channel: DispatcherChannel = "broadcast-channel") : DescriptiveDirective()
