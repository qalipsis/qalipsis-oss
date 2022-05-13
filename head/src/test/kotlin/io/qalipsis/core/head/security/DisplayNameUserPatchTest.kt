package io.qalipsis.core.head.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.core.head.jdbc.entity.UserEntity
import org.junit.jupiter.api.Test

internal class DisplayNameUserPatchTest {

    @Test
    internal fun `should update the display name when different`() {
        // given
        val user = UserEntity("", displayName = "the-name")
        val patch = DisplayNameUserPatch("the-other-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isTrue()
        assertThat(user).prop(UserEntity::displayName).isEqualTo("the-other-name")
    }

    @Test
    internal fun `should not update the display name when equal`() {
        // given
        val user = UserEntity("", displayName = "the-name")
        val patch = DisplayNameUserPatch("the-name")

        // when
        val result = patch.apply(user)

        // then
        assertThat(result).isFalse()
        assertThat(user).prop(UserEntity::displayName).isEqualTo("the-name")
    }
}