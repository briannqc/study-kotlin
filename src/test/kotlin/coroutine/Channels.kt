package coroutine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.Test

class Channels {

  private val logger: Logger = LogManager.getLogger(javaClass)

  @Test
  fun `Channels is conceptually very similar to BlockingQueue but not blocking`() = runBlocking {
    val channel = Channel<Int>()
    launch {
      for (x in 1..5) {
        val sq = x * x
        logger.info("Sending $sq")
        channel.send(sq)
      }
    }
    repeat(5) {
      val value = channel.receive()
      logger.info("Received $value")
    }
    println("Done")
  }

  @Test
  fun `Channel can be closed to indicate that no more elements are coming`() = runBlocking {
    val channel = Channel<Int>()
    launch {
      for (x in 1..5) {
        val sq = x * x
        logger.info("Sending $sq")
        channel.send(sq)
      }
      channel.close()
    }
    for (value in channel) {
      logger.info("Received $value")
    }
    logger.info("Done")
  }

  @ExperimentalCoroutinesApi
  @Test
  fun `Building channel producers with produce coroutine builder`() = runBlocking {
    val squares = produce {
      var x = 1
      while (x < 10) {
        logger.info("Sending $x")
        send(x++)
      }
    }
    squares.consumeEach {
      logger.info("Consuming $it")
    }
    logger.info("Done")
  }

  @ExperimentalCoroutinesApi
  @Test
  fun `Pipelines with channel`() = runBlocking {
    fun produceNumbers() = produce {
      var x = 1
      while (true) {
        logger.info("Sending $x")
        send(x++)
      }
    }
    fun squares(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
      for (x in numbers) {
        val sq = x * x
        logger.info("Sending $sq")
        send(sq)
      }
    }

    val numbers = produceNumbers()
    val squares = squares(numbers)
    repeat(5) {
      logger.info("Received ${squares.receive()}")
    }
    logger.info("Done")
    coroutineContext.cancelChildren()
  }

  @ExperimentalCoroutinesApi
  @Test
  fun `Prime numbers with pipeline`() = runBlocking {
    fun numbersFrom(start: Int) = produce {
      var x = start
      while (true) {
        logger.info("numbersFrom: Sending $x")
        send(x++)
      }
    }
    fun filter(numbers: ReceiveChannel<Int>, prime: Int) = produce {
      for (x in numbers) {
        logger.info("FILTER: x=$x prime=$prime")
        if (x % prime != 0) {
          logger.info("FILTER: Sending $x")
          send(x)
        }
      }
    }
    var cur = numbersFrom(2)
    repeat(10) {
      val prime = cur.receive()
      logger.info("prime: $prime")
      cur = filter(cur, prime)
    }
    coroutineContext.cancelChildren()
  }

  @ExperimentalCoroutinesApi
  @Test
  fun `Multiple coroutines may receive from the same channel (Fan-out)`() = runBlocking {
    fun produceNumbers() = produce {
      var x = 1
      while (true) {
        send(x++)
        delay(100)
      }
    }
    fun launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
      for (msg in channel) {
        logger.info("Processor $id received $msg")
      }
    }
    val producer = produceNumbers()
    repeat(5) { launchProcessor(it, producer) }
    delay(950)
    producer.cancel()
  }

  @Test
  fun `Multiple coroutines may send to the same channel (Fan-in)`() = runBlocking {
    suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
      while (true) {
        delay(time)
        channel.send(s)
      }
    }

    val channel = Channel<String>()
    launch { sendString(channel, "foo", 200L) }
    launch { sendString(channel, "BAR!", 500L) }
    repeat(6) {
      logger.info(channel.receive())
    }

    coroutineContext.cancelChildren()
  }

  @Test
  fun `Buffered channels`() = runBlocking {
    val channel = Channel<Int>(4)
    val sender = launch {
      repeat(10) {
        logger.info("Sending $it")
        channel.send(it)
      }
    }

    delay(1000)
    sender.cancel()
  }

  @Test
  fun `Channels are fair`() = runBlocking {
    data class Ball(var hits: Int)

    suspend fun player(name: String, table: Channel<Ball>) {
      for (ball in table) {
        ball.hits++
        logger.info("$name $ball")
        delay(300)
        table.send(ball)
      }
    }

    val table = Channel<Ball>()
    launch {
      player("ping", table)
    }
    launch {
      player("pong", table)
    }
    table.send(Ball(0))
    delay(1000)

    coroutineContext.cancelChildren()
  }

  @ObsoleteCoroutinesApi
  @Test
  fun `Ticker channels`() = runBlocking {
    val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0)
    var nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
    logger.info("Initial element is available immediately: $nextElement")

    nextElement = withTimeoutOrNull(50) { tickerChannel.receive() }
    logger.info("Next element is not ready in 50ms: $nextElement")

    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
    logger.info("Next element is ready in 100ms: $nextElement")

    logger.info("Consumer pauses for 150ms")
    delay(150)

    nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
    logger.info("Next element is available immediately after large consumer delay: $nextElement")

    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
    logger.info("Next element is ready in50ms after consumer pause in 150ms: $nextElement")

    tickerChannel.cancel()
  }
}
