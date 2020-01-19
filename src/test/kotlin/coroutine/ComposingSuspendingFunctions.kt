package coroutine

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class ComposingSuspendingFunctions {

  @Test
  fun `Suspending functions are invoked sequentially by default`() = runBlocking {
    val time = measureTimeMillis {
      val first = calculateTheFirstNumber()
      val second = calculateTheSecondNumber()
      val finalResult = first + second
      assertEquals(33, finalResult)
    }
    println("Completed in $time")
  }

  private suspend fun calculateTheFirstNumber(): Int {
    delay(700L)
    return 23
  }

  private suspend fun calculateTheSecondNumber(): Int {
    delay(700L)
    return 10
  }

  @Test
  fun `Use async to execute suspending functions concurrently, Note concurrency with coroutines is always explicit`() = runBlocking {
    val time = measureTimeMillis {
      val first = async { calculateTheFirstNumber() }
      val second = async { calculateTheSecondNumber() }

      println("Jobs are both started, this delay will not impact the total calculation time")
      delay(300L)
      val finalResult = first.await() + second.await()
      assertEquals(33, finalResult)
    }

    println("Completed in $time")
    assertTrue(time < 1000L)
  }

  @Test
  fun `async can be made lazy by setting its start parameter to CoroutineStart LAZY`() = runBlocking {
    val time = measureTimeMillis {
      val first = async(start = CoroutineStart.LAZY) { calculateTheFirstNumber() }
      val second = async(start = CoroutineStart.LAZY) { calculateTheSecondNumber() }

      println("Jobs did not started yet, this delay will increase the total calculation time")
      delay(300)

      println("""
        In lazy mode, it only starts the coroutine when its result is required be await or
        its Job's start function is invoked
      """.trimIndent())
      first.start()
      second.start()

      // If we do not start Jobs but await only in this calculation, it will lead to sequential behavior
      // because await start the job then wait for its finish
      val finalResult = first.await() + second.await()
      assertEquals(33, finalResult)
    }
    println("Completed in $time")
    assertTrue(time >= 1000L)
  }

  private fun calculateTheFirstNumberAsync() = GlobalScope.async {
    calculateTheFirstNumber()
  }

  private fun calculateTheSecondNumberAsync() = GlobalScope.async {
    calculateTheSecondNumber()
  }

  @Test
  fun `We can define async-style functions using the async coroutine builder with an explicit GlobalScope reference, that function is not suspending function and is STRONGLY DISCOURAGED`() {
    val time = measureTimeMillis {
      // We can initiate async style actions outside of a coroutine
      val first = calculateTheFirstNumberAsync()
      val second = calculateTheSecondNumberAsync()

      // But waiting for a result must involve either suspending or blocking.
      // Here we use `runBlocking { }` to block the main thread waiting for the result
      runBlocking {
        println("The async-style actions are already started, this delay will not increase computation time")
        delay(300L)

        val finalResult = first.await() + second.await()
        assertEquals(33, finalResult)
      }
    }
    println("Completed in $time")
    assertTrue(time < 1000L)

    println("""
      This programming style with async functions is provided here only for illustration, because
      it is a popular in other programming languages. Using this style in Kotlin coroutines is
      STRONGLY DISCOURAGED because if there is some logic error between function's start and await,
      and program no longer need its result, the function still running in the background. This
      problem does not happen with structured concurrency.
    """.trimIndent())
  }

  private suspend fun concurrentSum(): Int = coroutineScope {
    println("""
      If something goes wrong inside the code of concurrentSum function and it throws an exception,
      all the coroutines that were launched in its scope will be cancelled.
    """.trimIndent())
    val first = async { calculateTheFirstNumber() }
    val second = async { calculateTheSecondNumber() }
    first.await() + second.await()
  }

  @Test
  fun `Structured concurrency with async`() = runBlocking {
    val time = measureTimeMillis {
      val sum = concurrentSum()
      assertEquals(33, sum)
    }
    println("Completed in $time")
  }

  private suspend fun failedConcurrentSum(): Int = coroutineScope {
    val first = async {
      try {
        delay(Long.MAX_VALUE) // Emulates very long computation
        42
      } finally {
        println("First child was cancelled")
      }
    }
    val second = async<Int> {
      println("Second child throws an exception")
      throw ArithmeticException()
    }
    first.await() + second.await()
  }

  @Test
  fun `Cancellation is always propagated through coroutines hierarchy`() = runBlocking<Unit> {
    try {
      failedConcurrentSum()
    } catch (e: ArithmeticException) {
      println("Computation failed with ArithmeticException")
    }
  }
}
