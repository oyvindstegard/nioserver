package net.stegard.nioserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class NIOServerTests {

  @Test
  fun `multiple messages single client`() {
    NIOServer().use { server ->
      // Write messages to server through client socket
      Socket(server.getHost(), server.getPort()).getOutputStream().use { client ->
        client.write("Hello server".toByteArray(UTF_8))
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
        client.write("How are you ?".toByteArray(UTF_8))
        client.write(" Good ?".toByteArray(UTF_8))
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
        client.write("Fine\u0000thank you☺".toByteArray(UTF_8))
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
        client.write("Fin".toByteArray(UTF_8))
      }

      assertEquals("Hello server", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("How are you ? Good ?", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("Fine", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("thank you☺", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("", server.takeMessage(Duration.ofMillis(500)))
      assertEquals("Fin", server.takeMessage(Duration.ofMillis(500)))
      assertNull(server.takeMessage(Duration.ofMillis(500)))

      assertEquals(8, server.messageReceptionCount())
    }

  }

  @Test
  fun `empty message without end marker`() {
    NIOServer().use { server ->
      Socket(server.getHost(), server.getPort()).getOutputStream().use {
      }
      assertNull(server.takeMessage(Duration.ofSeconds(1)))
    }
  }

  @Test
  fun `empty message with end marker`() {
    NIOServer().use { server ->
      Socket(server.getHost(), server.getPort()).getOutputStream().use { client ->
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
      }
      assertEquals("", server.takeMessage(Duration.ofSeconds(10)))
    }
  }

  @Test
  fun `one message without end marker`() {
    NIOServer().use { server ->
      Socket(server.getHost(), server.getPort()).getOutputStream().use { client ->
        client.write("One message".toByteArray())
      }

      assertEquals("One message", server.takeMessage(Duration.ofSeconds(10)))
    }
  }

  @Test
  fun `one message with end marker`() {
    NIOServer().use { server ->
      Socket(server.getHost(), server.getPort()).getOutputStream().use { client ->
        client.write("One message".toByteArray())
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
      }

      assertEquals("One message", server.takeMessage(Duration.ofSeconds(10)))
    }
  }

  @Test
  fun `one message as single byte writes with end marker`() {
    val testMessage = "A message split up into multiple writes"
    NIOServer().use { server ->
      Socket(server.getHost(), server.getPort()).getOutputStream().use { client ->
        for (b in testMessage.toByteArray()) {
          client.write(b.toInt())
        }
        client.write(NIOServer.END_MESSAGE_MARKER.toInt())
      }

      assertEquals(testMessage, server.takeMessage(Duration.ofSeconds(10)))
    }
  }

  @Test
  fun `multiple messages in single write`() {
    val writeBytes = "A\u0000B\u0000C\u0000D\u0000".toByteArray()
    NIOServer().use { server ->
      Socket(server.getHost(), server.getPort()).getOutputStream().use { client ->
        client.write(writeBytes)
      }

      assertEquals("A", server.takeMessage(Duration.ofSeconds(1)))
      assertEquals("B", server.takeMessage(Duration.ofSeconds(1)))
      assertEquals("C", server.takeMessage(Duration.ofSeconds(1)))
      assertEquals("D", server.takeMessage(Duration.ofSeconds(1)))
      assertNull(server.takeMessage(Duration.ofMillis(200)))
      assertEquals(4, server.messageReceptionCount())
    }
  }

  @Test
  fun `threaded multiple clients two messages each`() {
    val numClients = 1000
    NIOServer(receiveBufferSize = 1024, messageStoreCapacity = 3000).use { server ->

      val clientCompletedCount = AtomicInteger(0)

      val clientRunnables = IntRange(1, numClients).map { id ->
        Runnable {
          Socket(server.getHost(), server.getPort()).getOutputStream().use {
            it.write("Hello from client-$id".toByteArray())
            it.write(NIOServer.END_MESSAGE_MARKER.toInt())
            for (b in "Goodbye from client-$id".toByteArray()) {
              it.write(b.toInt())
            }
            it.write(NIOServer.END_MESSAGE_MARKER.toInt())
          }
          clientCompletedCount.incrementAndGet()
        }
      }

      val threads = clientRunnables.map { Thread(it) }
      println("Starting ${threads.size} client threads ..")
      threads.forEach { it.start() }
      println("Waiting for client threads ..")
      threads.forEach { it.join() }
      println("Done.")

      var msgCount = 0
      while (server.takeMessage(Duration.ofMillis(500)) != null) {
        msgCount += 1
      }

      assertEquals(1000, clientCompletedCount.get(), "Expected 1000 clients to have successfully completed sending")
      assertEquals(2000, msgCount, "Expect 2000 messages, 2 from each of 1000 clients")
      assertEquals(msgCount.toLong(), server.messageReceptionCount(), "Stored message count not equal to total server message count")
    }

  }

}