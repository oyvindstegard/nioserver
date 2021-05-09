package net.stegard.nioserver

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private fun log(message: String) = println("[${LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)}] Main: ${message}")

/**
 * Starts a simple command line server that prints received messages to console as they arrive.
 */
fun main(args: Array<String>) {

    val (host, port) = try {
        when {
            args.size == 0 -> Pair("localhost", 0)
            args.size == 1 && !args[0].contains(":") -> Pair("localhost", args[0].toInt())
            args.size == 1 && args[0].contains(":") -> Pair(
                args[0].substringBefore(':'),
                args[0].substringAfter(':').toInt()
            )
            args.size == 2 -> Pair(args[0], args[1].toInt())
            else -> throw IllegalArgumentException()
        }
    } catch (e: Exception) {
        println("Use args: [HOST:]PORT, default is random port on localhost")
        return
    }

    val mainThread = Thread.currentThread()
    Runtime.getRuntime().addShutdownHook(Thread {
        mainThread.interrupt()
        Thread.sleep(200)
    })

    NIOServer(host, port, messageStoreCapacity = 65536).use { server ->
        log("server listening on ${server.getHost()}:${server.getPort()}")
        log("press CTRL+C to exit")
        while (! Thread.currentThread().isInterrupted) {
            try {
                server.takeMessage(Duration.ofSeconds(1))?.let { log("rcv: $it") }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    log("exit")
}
