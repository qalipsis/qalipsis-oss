package io.qalipsis.core.head.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.core.head.jdbc.entity.UserEntity
import org.junit.jupiter.api.Test

internal class UsernameUserPatchTest {

    @Test
    internal fun `should update the user name when different`() {
        // given
        val user = UserEntity(username = "the-name")
        val patch = UsernameUserPatch("the-other-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isTrue()
        assertThat(user).prop(UserEntity::username).isEqualTo("the-other-name")
    }

    @Test
    internal fun `should not update the user name when equal`() {
        // given
        val user = UserEntity(username = "the-name")
        val patch = UsernameUserPatch("the-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isFalse()
        assertThat(user).prop(UserEntity::username).isEqualTo("the-name")
    }
}