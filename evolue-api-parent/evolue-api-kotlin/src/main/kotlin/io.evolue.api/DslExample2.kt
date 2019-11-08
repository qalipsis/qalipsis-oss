package io.evolue.api

fun example() {
    action("Test", MockedRequest<Unit, Unit, List<Int>>())
            .parallel {
                action("Test 2", MockedRequest<Pair<Unit, List<Int>>, Unit, Unit>())

                mapAction()
            }
            .complete()
}