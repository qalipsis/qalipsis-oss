package io.qalipsis.core.cross.directives

import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.ListDirectiveReference
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.QueueDirectiveReference
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirectiveReference

/**
 *
 * @author Eric Jessé
 */

internal class TestQueueDirective(values: List<Int>) : QueueDirective<Int, QueueDirectiveReference<Int>>(values) {

    private val ref = TestQueueDirectiveReference("my-queue-directive")

    override fun toReference(): QueueDirectiveReference<Int> {
        return ref
    }
}

internal class TestQueueDirectiveReference(key: DirectiveKey) : QueueDirectiveReference<Int>(key)


internal class TestListDirective(values: List<Int>) : ListDirective<Int, ListDirectiveReference<Int>>(values) {

    private val ref = TestListDirectiveReference("my-list-directive")

    override fun toReference(): ListDirectiveReference<Int> {
        return ref
    }
}

internal class TestListDirectiveReference(key: DirectiveKey) : ListDirectiveReference<Int>(key)


internal class TestSingleUseDirective(value: Int) : SingleUseDirective<Int, SingleUseDirectiveReference<Int>>(value) {

    private val ref = TestSingleUseDirectiveReference("my-single-use-directive")

    override fun toReference(): SingleUseDirectiveReference<Int> {
        return ref
    }
}

internal class TestSingleUseDirectiveReference(key: DirectiveKey) : SingleUseDirectiveReference<Int>(key)

internal class TestDescriptiveDirective : DescriptiveDirective()
