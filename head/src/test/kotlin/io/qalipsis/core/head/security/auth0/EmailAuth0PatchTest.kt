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

internal class EmailAuth0PatchTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should update the email address when different`() = testDispatcherProvider.runTest {
        // given
        val user = User().apply { email = "foo@bar.com" }
        val patch = EmailAuth0Patch("foo@acme.com")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isTrue()
        assertThat(user).prop(User::getEmail).isEqualTo("foo@acme.com")
    }

    @Test
    internal fun `should not update the email address when equal`() = testDispatcherProvider.runTest {
        // given
        val user = User().apply { email = "foo@bar.com" }
        val patch = EmailAuth0Patch("foo@bar.com")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isFalse()
        assertThat(user).prop(User::getEmail).isEqualTo("foo@bar.com")
    }
}