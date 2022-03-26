package io.qalipsis.runtime.test

import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Helper to start a new JVM process.
 *
 * @author Eric Jessé
 */
class JvmProcessUtils {

    private val processes = concurrentList<Process>()

    /**
     * Starts a new JVM reusing the classpath of the caller.
     *
     * @param jvmOptions options for the JVM
     * @param mainClass main class to start
     * @param arguments arguments to provide to the program
     */
    fun startNewJavaProcess(
        mainClass: KClass<*>,
        arguments: Array<String> = emptyArray(),
        jvmOptions: Array<String> = emptyArray(),
        workingDirectory: File = File(".")
    ): ProcessDescriptor {
        return startNewJavaProcess(mainClass.qualifiedName!!, arguments, jvmOptions, workingDirectory)
    }

    /**
     * Starts a new JVM reusing the classpath of the caller.
     *
     * @param jvmOptions options for the JVM
     * @param mainClass main class to start
     * @param arguments arguments to provide to the program
     */
    fun startNewJavaProcess(
        mainClass: String,
        arguments: Array<String> = emptyArray(),
        jvmOptions: Array<String> = emptyArray(),
        workingDirectory: File = File(".")
    ): ProcessDescriptor {
        val processBuilder = createProcessBuilder(jvmOptions, mainClass, arguments, workingDirectory)

        val outputFile = Files.createTempFile("", "log").toFile()
        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputFile))
        val errorFile = Files.createTempFile("", "log").toFile()
        processBuilder.redirectError(ProcessBuilder.Redirect.to(errorFile))
        val process = processBuilder.start()
        processes += process
        process.onExit().handleAsync { p, _ ->
            processes.removeIf { it === p }
        }
        log.debug { "The process ${process.pid()} started" }
        return ProcessDescriptor(process, outputFile, errorFile).also {
            it.jvmProcessUtils = this
        }
    }

    /**
     * Creates a new process reusing the current classpath.
     *
     * @param jvmOptions options for the JVM
     * @param mainClass main class to start
     * @param arguments arguments to provide to the program
     */
    private fun createProcessBuilder(
        jvmOptions: Array<String>,
        mainClass: String,
        arguments: Array<String>,
        workingDirectory: File
    ): ProcessBuilder {
        val jvm = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val classpath = System.getProperty("java.class.path")
        log.trace { "Classpath: $classpath" }
        log.trace { "Working directory: ${System.getProperty("user.dir")}" }

        val command = mutableListOf(jvm)
        command += jvmOptions
        command += mainClass
        command += arguments

        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(workingDirectory)
        processBuilder.environment()["CLASSPATH"] = classpath
        processBuilder.environment()["MICRONAUT_CONDITION_LOGGING_LEVEL"] = "TRACE"
        return processBuilder
    }

    /**
     * Kills all active processes.
     */
    fun shutdown() {
        log.debug { "Killing ${processes.size} processes" }
        processes.forEach { process ->
            tryAndLog(log) {
                process.destroy()
            }
        }
        processes.clear()
    }

    data class ProcessDescriptor(
        val process: Process,
        val outputFile: File,
        val errorFile: File,
    ) {

        internal lateinit var jvmProcessUtils: JvmProcessUtils

        val output: String
            get() = outputFile.readBytes().decodeToString()

        val outputLines: List<String>
            get() = outputFile.readLines()

        val error: String
            get() = errorFile.readBytes().decodeToString()

        val errorLines: List<String>
            get() = errorFile.readLines()

        fun await(timeout: Duration) {
            log.debug { "Awaiting process ${process.pid()}..." }
            try {
                process.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                log.error { "The process ${process.pid()} did not finish within $timeout" }
                throw e
            } finally {
                log.info { "Process ${process.pid()} output: \n\n${output}" }
                if (error.isNotBlank()) {
                    log.error { "Process ${process.pid()} error: \n\n${error}" }
                }
                kill()
            }
        }

        fun kill() {
            tryAndLog(JvmProcessUtils.log) {
                process.destroy()
            }
            jvmProcessUtils.processes.removeIf { it === process }
        }

        private companion object {
            val log = logger()
        }
    }

    private companion object {
        val log = logger()
    }
}