package io.qalipsis.api.orchestration.directives

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.qalipsis.core.directives.DescriptiveDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveKey
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.directives.ListDirective
import io.qalipsis.core.directives.ListDirectiveReference
import io.qalipsis.core.directives.QueueDirective
import io.qalipsis.core.directives.QueueDirectiveReference
import io.qalipsis.core.directives.SingleUseDirective
import io.qalipsis.core.directives.SingleUseDirectiveReference
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DirectiveKindTest {

    private lateinit var json: Json

    @BeforeEach
    fun setUp() {
        json = Json(builderAction = {
            serializersModule = module
        })
    }

    @Test
    fun `should be able to serialize list reference directive as base class`() {
        val directive: ListDirectiveReference<String> = ListDirectiveReferenceImpl<String>("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<ListDirectiveReference<String>>(jsonString)
        assertThat(convertedDirective).all {
            prop(ListDirectiveReference<String>::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize list reference directive implementation`() {
        val directive: ListDirectiveReferenceImpl<String> = ListDirectiveReferenceImpl<String>("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<ListDirectiveReferenceImpl<String>>(jsonString)
        assertThat(convertedDirective).all {
            prop(ListDirectiveReferenceImpl<String>::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize queue reference directive as base class`() {
        val directive: QueueDirectiveReference<String> = QueueDirectiveReferenceImpl<String>("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<QueueDirectiveReference<String>>(jsonString)
        assertThat(convertedDirective).all {
            prop(QueueDirectiveReference<String>::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize queue reference directive implementation`() {
        val directive: QueueDirectiveReferenceImpl<String> = QueueDirectiveReferenceImpl<String>("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<QueueDirectiveReferenceImpl<String>>(jsonString)
        assertThat(convertedDirective).all {
            prop(QueueDirectiveReferenceImpl<String>::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize single use directive as base class`() {
        val directive: SingleUseDirectiveReference<String> = SingleUseDirectiveReferenceImpl<String>("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<SingleUseDirectiveReference<String>>(jsonString)
        assertThat(convertedDirective).all {
            prop(SingleUseDirectiveReference<String>::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize single use directive reference implementation`() {
        val directive: SingleUseDirectiveReferenceImpl<String> =
            SingleUseDirectiveReferenceImpl<String>("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<SingleUseDirectiveReferenceImpl<String>>(jsonString)
        assertThat(convertedDirective).all {
            prop(SingleUseDirectiveReferenceImpl<String>::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize descriptive directive as base class`() {
        val directive: DescriptiveDirective = DescriptiveDirectiveImpl("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<DescriptiveDirective>(jsonString)
        assertThat(convertedDirective).all {
            prop(DescriptiveDirective::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize descriptive directive implementation`() {
        val directive = DescriptiveDirectiveImpl("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<DescriptiveDirectiveImpl>(jsonString)
        assertThat(convertedDirective).all {
            prop(DescriptiveDirectiveImpl::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize directive as base class`() {
        val directive: Directive = DirectiveImpl("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<Directive>(jsonString)
        assertThat(convertedDirective).all {
            prop(Directive::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize directive implementation`() {
        val directive = DirectiveImpl("123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<DirectiveImpl>(jsonString)
        assertThat(convertedDirective).all {
            prop(DirectiveImpl::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize single use directive`() {
        val directive: SingleUseDirective<String, SingleUseDirectiveReference<String>> =
            SingleUseDirectiveImpl("key", "123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective =
            json.decodeFromString<SingleUseDirective<String, SingleUseDirectiveReference<String>>>(jsonString)
        assertThat(convertedDirective).all {
            prop(SingleUseDirective<String, SingleUseDirectiveReference<String>>::key).isEqualTo("key")
            prop(SingleUseDirective<String, SingleUseDirectiveReference<String>>::value).isEqualTo("123")
        }
    }

    @Test
    fun `should be able to serialize single use directive implementation`() {
        val directive = SingleUseDirectiveImpl("key", "123", "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<SingleUseDirectiveImpl>(jsonString)
        assertThat(convertedDirective).all {
            prop(SingleUseDirectiveImpl::key).isEqualTo("key")
            prop(SingleUseDirectiveImpl::value).isEqualTo("123")
        }
    }

    @Test
    fun `should be able to serialize queue directive`() {
        val directive: QueueDirective<String, QueueDirectiveReference<String>> =
            QueueDirectiveImpl("key", listOf("123"), "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective =
            json.decodeFromString<QueueDirective<String, QueueDirectiveReference<String>>>(jsonString)
        assertThat(convertedDirective).all {
            prop(QueueDirective<String, QueueDirectiveReference<String>>::key).isEqualTo("key")
            prop(QueueDirective<String, QueueDirectiveReference<String>>::values).isEqualTo(listOf("123"))
        }
    }

    @Test
    fun `should be able to serialize queue directive implementation`() {
        val directive = QueueDirectiveImpl("key", listOf("123"), "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<QueueDirectiveImpl>(jsonString)
        assertThat(convertedDirective).all {
            prop(QueueDirectiveImpl::key).isEqualTo("key")
            prop(QueueDirectiveImpl::values).isEqualTo(listOf("123"))
        }
    }

    @Test
    fun `should be able to serialize list directive`() {
        val directive: ListDirective<String, ListDirectiveReference<String>> =
            ListDirectiveImpl("key", listOf("123"), "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective =
            json.decodeFromString<ListDirective<String, ListDirectiveReference<String>>>(jsonString)
        assertThat(convertedDirective).all {
            prop(ListDirective<String, ListDirectiveReference<String>>::key).isEqualTo("key")
            prop(ListDirective<String, ListDirectiveReference<String>>::values).isEqualTo(listOf("123"))
        }
    }

    @Test
    fun `should be able to serialize list directive implementation`() {
        val directive = ListDirectiveImpl("key", listOf("123"), "unicast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<ListDirectiveImpl>(jsonString)
        assertThat(convertedDirective).all {
            prop(ListDirectiveImpl::key).isEqualTo("key")
            prop(ListDirectiveImpl::values).isEqualTo(listOf("123"))
        }
    }

    @Serializable
    @SerialName("listDirectiveReferenceImpl")
    class ListDirectiveReferenceImpl<String>(override val key: DirectiveKey, override val channel: DispatcherChannel) :
        ListDirectiveReference<String>()

    @Serializable
    @SerialName("queueDirectiveReferenceImpl")
    class QueueDirectiveReferenceImpl<String>(override val key: DirectiveKey, override val channel: DispatcherChannel) :
        QueueDirectiveReference<String>()

    @Serializable
    @SerialName("singleUseDirectiveReferenceImpl")
    class SingleUseDirectiveReferenceImpl<String>(
        override val key: DirectiveKey,
        override val channel: DispatcherChannel
    ) : SingleUseDirectiveReference<String>()

    @Serializable
    @SerialName("singleUseDirectiveImpl")
    class SingleUseDirectiveImpl(
        override val key: DirectiveKey,
        override val value: String,
        override val channel: DispatcherChannel
    ) : SingleUseDirective<String, SingleUseDirectiveReference<String>>() {

        override fun toReference(): SingleUseDirectiveReference<String> {
            return SingleUseDirectiveReferenceImpl(key, channel)
        }
    }

    @Serializable
    @SerialName("queueDirectiveImpl")
    class QueueDirectiveImpl(
        override val key: DirectiveKey,
        override val values: List<String>,
        override val channel: DispatcherChannel
    ) : QueueDirective<String, QueueDirectiveReference<String>>() {

        override fun toReference(): QueueDirectiveReference<String> {
            return QueueDirectiveReferenceImpl(key, channel)
        }
    }

    @Serializable
    @SerialName("listDirectiveImpl")
    class ListDirectiveImpl(
        override val key: DirectiveKey,
        override val values: List<String>,
        override val channel: DispatcherChannel
    ) : ListDirective<String, ListDirectiveReference<String>>() {

        override fun toReference(): ListDirectiveReference<String> {
            return ListDirectiveReferenceImpl(key, channel)
        }
    }

    @Serializable
    @SerialName("descriptiveDirectiveImpl")
    class DescriptiveDirectiveImpl(override val key: DirectiveKey, override val channel: DispatcherChannel) :
        DescriptiveDirective()


    @Serializable
    @SerialName("directiveImpl")
    class DirectiveImpl(override val key: DirectiveKey, override val channel: DispatcherChannel) : Directive()

    private val module = SerializersModule {
        polymorphic(ListDirectiveReference::class) {
            subclass(
                ListDirectiveReferenceImpl::class,
                ListDirectiveReferenceImpl.serializer(PolymorphicSerializer(ListDirectiveReferenceImpl::class)) as KSerializer<ListDirectiveReferenceImpl<*>>
            )
        }
        polymorphic(DescriptiveDirective::class) {
            subclass(DescriptiveDirectiveImpl::class)
        }

        polymorphic(QueueDirectiveReference::class) {
            subclass(
                QueueDirectiveReferenceImpl::class,
                QueueDirectiveReferenceImpl.serializer(PolymorphicSerializer(QueueDirectiveReferenceImpl::class)) as KSerializer<QueueDirectiveReferenceImpl<*>>
            )
        }

        polymorphic(SingleUseDirectiveReference::class) {
            subclass(
                SingleUseDirectiveReferenceImpl::class,
                SingleUseDirectiveReferenceImpl.serializer(PolymorphicSerializer(SingleUseDirectiveReferenceImpl::class)) as KSerializer<SingleUseDirectiveReferenceImpl<*>>
            )
        }

        polymorphic(SingleUseDirective::class) {
            subclass(SingleUseDirectiveImpl::class, SingleUseDirectiveImpl.serializer())
        }

        polymorphic(ListDirective::class) {
            subclass(ListDirectiveImpl::class, ListDirectiveImpl.serializer())
        }

        polymorphic(QueueDirective::class) {
            subclass(QueueDirectiveImpl::class, QueueDirectiveImpl.serializer())
        }

        polymorphic(Directive::class) {
            subclass(DirectiveImpl::class)
        }
    }
}