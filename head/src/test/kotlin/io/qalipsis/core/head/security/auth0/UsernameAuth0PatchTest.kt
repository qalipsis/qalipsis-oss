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

internal class UsernameAuth0PatchTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should update the user name when different`() = testDispatcherProvider.runTest {
        // given
        val user = User().apply { username = "the-name" }
        val patch = UsernameAuth0Patch("the-other-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isTrue()
        assertThat(user).prop(User::getUsername).isEqualTo("the-other-name")
    }

    @Test
    internal fun `should not update the user name when equal`() = testDispatcherProvider.runTest {
        // given
        val user = User().apply { username = "the-name" }
        val patch = UsernameAuth0Patch("the-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isFalse()
        assertThat(user).prop(User::getUsername).isEqualTo("the-name")
    }
}