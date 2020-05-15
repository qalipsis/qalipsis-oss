package io.evolue.api

import assertk.assertThat
import java.time.Duration

fun example() {

    action("Test", { anyRequest<ByteArray>() })
            .configure {
                timeout = Duration.ZERO
                // ...
            }
            .filter { input -> input.response != null }
            .map { _, bytes -> listOf(1, 2, 3) }

            .parallel {
                action("Test 2") { items -> otherRequest(items) }
                        .configure { }

                assert("Assert 1") { items -> assertThat { }; items.response }
                        .configure {

                        }
                        .action("Test 3") { ints -> anotherRequest(ints) }
                        .map { list, map -> arrayOf(true, false) }
                        .delay(Duration.ZERO)
                        .assert("Test 4", { actionOutput -> assertThat { } })
            }
            .complete()

}