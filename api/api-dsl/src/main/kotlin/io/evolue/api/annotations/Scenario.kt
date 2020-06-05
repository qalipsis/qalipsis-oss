package io.evolue.api.annotations

/**
 * Annotation to mark a method as a scenario specification. The function will be executed at startup
 * of the factory to load the specification. The method does not have to return something. Any returned value
 * will be ignored.
 *
 * <code>
 * @Scenario
 * fun createMyScenario() {
 *   scenario("my-scenario) {
 *          // Configure you scenario here.
 *      }
 *      // Then add steps.
 *      .justDo { context ->
 *          // ...
 *      }
 * }
 * <code>
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Scenario