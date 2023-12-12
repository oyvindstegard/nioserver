package net.stegard.nioserver

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A single threaded server using non-blocking I/O that can receive UTF-8-encoded string messages. Implemented with
 * java.nio and serves as demo/example code.
 *
 * Stream protocol: variable length UTF-8 encoded messages, up to max receive buffer size each, with end message marker byte.
 */
class NIOServer: Runnable, AutoCloseable {

  companion object {
    /**
     * Marker byte for end of a single message in a stream.
     */
    @JvmStatic
    val END_MESSAGE_MARKER: Byte = 0x00
  }

  private val serverSocket: ServerSocketChannel
  private val selector: Selector
  private val messages: ArrayBlockingQueue<String>
  private val recvBufSize: Int
  private val messageReceptionCount = AtomicLong(0)

  constructor(host: String = "localhost",
              port: Int = 0,
              receiveBufferSize: Int = 32 * 1024,
              messageStoreCapacity: Int = 1024,
              acceptBacklog: Int = 100000) {

    recvBufSize = receiveBufferSize
    messages = ArrayBlockingQueue(messageStoreCapacity)
    selector = Selector.open()
    serverSocket = ServerSocketChannel.open().apply {
      bind(InetSocketAddress(host, port), acceptBacklog)
      configureBlocking(false)
      register(selector, SelectionKey.OP_ACCEPT)
    }

    // Server runs in a single dedicated daemon thread
    Thread(this).apply {
      name = "NIOServer-thread-${getPort()}"
      isDaemon = true
      start()
    }

    log("NIOServer started on port ${getPort()}")
  }

  /**
   * Get port of running server
   */
  fun getPort(): Int = (serverSocket.localAddress as InetSocketAddress).port

  /**
   * Get host of running server.
   */
  fun getHost(): String = (serverSocket.localAddress as InetSocketAddress).hostString

  /**
   * Get next stored message, with timeout resulting in a null value when there are no new messages
   * available.
   *
   * @param timeout wait at most this amount of time for a message to become available, default is no waiting allowed
   * @return next message, or <code>null</code> if no message is available within the
   * @throws InterruptedException if thread is interrupted while waiting for a message
   */
  fun takeMessage(timeout: Duration = Duration.ofSeconds(0)): String? =
    messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)

  /**
   * Returns total number of messages received during the lifetime of the server instance.
   */
  fun messageReceptionCount() = messageReceptionCount.get()

  private fun log(message: String) = println("${Thread.currentThread().name}: ${message}")

  private fun storeMessage(buffer: ByteBuffer) {
    messageReceptionCount.incrementAndGet()
    val message = UTF_8.decode(buffer).toString()
    if (!messages.offer(message)) {
      log("warning: message storage full, discarding message: ${message}")
    }
  }

  /**
   * Receive data from a client, zero or more individual messages per invocation.
   */
  private fun SelectionKey.receive() {
    val channel = channel() as SocketChannel
    val buffer = attachment() as ByteBuffer
    try {
      val read = channel.read(buffer)
      if (read == 0) {
        return
      }

      var i = 0
      while (i < buffer.position()) {
        if (buffer.get(i) == END_MESSAGE_MARKER) {
          storeMessage(buffer.duplicate().position(i).flip())
          buffer.limit(buffer.position()).position(i + 1).compact()
          i = 0
        } else i += 1
      }

      if (!buffer.hasRemaining()) {
        log("error: client buffer overflow, too big message, discarding buffer")
        buffer.clear()
      }

      if (read == -1) {
        if (buffer.position() > 0) {
          storeMessage(buffer.flip())
        }
        channel.close()
        cancel()
      }
    } catch (e: IOException) {
      log("error reading from client: ${e.javaClass}: ${e.message}")
    }
  }

  /**
   * Accept a new client connection.
   */
  private fun SelectionKey.acceptClient() {
    val clientChannel = (channel() as ServerSocketChannel).accept()
    clientChannel.configureBlocking(false)
    // Register client for reads and allocate a private message reception buffer
    clientChannel.register(selector, SelectionKey.OP_READ).attach(ByteBuffer.allocate(recvBufSize))
  }

  /**
   * Server main loop.
   */
  override fun run() {
    while (selector.isOpen) { // While alive, we loop and process I/O events in a non-blocking fashion
      try {
        selector.select { selectionKey ->
          if (selectionKey.isAcceptable) {
            selectionKey.acceptClient()
          }
          if (selectionKey.isReadable) {
            selectionKey.receive()
          }
        }
      } catch (closed: ClosedSelectorException) {
      } catch (e: IOException) {
        log("error: ${e.javaClass.simpleName}: ${e.message}")
      }
    }
    log("closed")
  }

  override fun close() {
    selector.close()
    serverSocket.close()
  }

}