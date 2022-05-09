package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.auth0.json.mgmt.users.User
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class NameAuth0PatchTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should update the name when different`() = testDispatcherProvider.runTest {
        // given
        val user = User().apply { name = "the-name" }
        val patch = NameAuth0Patch("the-other-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isTrue()
        assertThat(user).prop(User::getName).isEqualTo("the-other-name")
    }

    @Test
    internal fun `should not update the name when equal`() = testDispatcherProvider.runTest {
        // given
        val user = User().apply { name = "the-name" }
        val patch = NameAuth0Patch("the-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isFalse()
        assertThat(user).prop(User::getName).isEqualTo("the-name")
    }
}