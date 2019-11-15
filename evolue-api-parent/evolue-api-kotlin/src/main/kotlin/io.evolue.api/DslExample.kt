package io.evolue.api

import java.time.Duration

fun example() {
    action("Test", { anyRequest<Unit, ByteArray, List<Int>>() })
            .configure {
                timeout = Duration.ZERO
                // ...
            }
            .parallel {
                action("Test 2", { _, items -> otherRequest<Pair<Unit, List<Int>>, Unit, Unit>() })
                        .configure { }

                assert("Assert 1", { input: Unit, response: ByteArray, output: List<Int> -> listOf("A", "B") })
                        .configure {

                        }
                        .action("Test 3", { actionInput, actionOutput, assertInput, assertOutput ->
                            anotherRequest<ActionAfterAssertion<Unit, List<Int>, Unit, List<String>>, String, List<Int>>()
                        })
                        .mapInput { actionAfterAssertion -> listOf(true, false) }
                        .map { list -> listOf(123L, 345L) }
                        .delay(Duration.ZERO)
                        .assert("Test 4", { input, response, output -> })

            }
            .complete()
}