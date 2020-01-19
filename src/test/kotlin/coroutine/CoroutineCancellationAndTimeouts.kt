package coroutine

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

class CoroutineCancellationAndTimeouts {

    @Test
    fun `The launch function returns a job that can be used to cancel the running coroutine`() = runBlocking {
        val job = launch {
            repeat(1000) { i ->
                println("Job: I'm sleeping $i ...")
                delay(500L)
            }
        }
        delay(1300L)
        println("Main: I'm tired of waiting, I'm cancelling you Job")
        job.cancel()
        println("Main: Cancelled, Did you complete job? Job: ${if (job.isCompleted) "Yes" else "Not yet"}")
        job.join() // Waits for job's completion
        println("Job: Alright, I completed")
        println("Main: Now I can quit.")
    }

    @Test
    fun `If a coroutine is working in a computation and does not check for cancellation, then it cannot be cancelled`() = runBlocking {
        val startTime = System.currentTimeMillis()
        val job = launch(Dispatchers.Default) {
            var nextPrintTime = startTime
            var i = 0
            while (i < 5) { // Computation loop, just wastes CPU
                if (System.currentTimeMillis() >= nextPrintTime) {
                    println("Job: I'm sleeping ${i++}")
                    nextPrintTime += 500L
                }
            }
        }
        delay(1300L)
        println("Main: I'm tired of waiting, I'm about to cancel and wait for Job to complete!")
        job.cancelAndJoin()
        println("Main: Now I can quit!")
    }

    @Test
    fun `Making computation code cancellable by replacing computation loop with check the cancellation status`() = runBlocking {
        val startTime = System.currentTimeMillis()
        val job = launch(Dispatchers.Default) {
            var nextPrintTime = startTime
            var i = 0
            while (isActive) { // Cancellable computation loop
                if (System.currentTimeMillis() >= nextPrintTime) {
                    println("Job: I'm sleeping ${i++}")
                    nextPrintTime += 500
                }
            }
        }
        delay(1300L)
        println("Main: I'm tired of waiting! I'm cancelling Job")
        job.cancelAndJoin()
        println("Main: Now I can quit")
    }

    @Test
    fun `Cancellable suspending functions throw CancellationException on cancellation which can be handled in the usual way`() = runBlocking {
        val job = launch {
            try {
                repeat(1000) { i ->
                    println("Job: I'm sleeping $i")
                    delay(500L)
                }
            } catch (ex: CancellationException) {
                println("Job: Ops CancellationException!!! I'm cancelled, I will run finally")
            } finally {
                println("Job: I'm running finally")
            }
        }
        delay(1300L)
        println("Main: I'm tired of waiting, I'm cancelling Job")
        job.cancelAndJoin()
        println("Main: Now I can quit")
    }

    @Test
    fun `Run non-cancellable block with withContext(NonCancellable) in (rare) case when you need to suspend in a cancelled coroutine`() = runBlocking {
        val job = launch {
            try {
              repeat(1000) { i ->
                  println("Job: I'm sleeping $i")
                  delay(500L)
              }
            } finally {
              withContext(NonCancellable) {
                  println("Job: I'm running finally")
                  delay(1000L)
                  println("Job: And I've just delayed for 1 sec because I'm not cancellable")
              }
            }
        }
        delay(1300L)
        println("Main: I'm tired of waiting, I'm cancelling Job")
        job.cancelAndJoin()
        println("Main: Now I can quit")
    }

    @Test
    fun `We can set timeout for coroutine with withTimeout(timeMillis)`() = runBlocking {
        println("Main: I'm starting Job with 1300ms timeout")
        val result = withTimeoutOrNull(1300L) {
            try {
                repeat(1000) { i ->
                    println("I'm sleeping $i")
                    delay(500L)
                }
            } catch (ex: TimeoutCancellationException) {
                println("Job: Ops TimeoutCancellationException!!! I run for too long, exceed the timeout")
            }
            "Done"
        }
        println("Result is $result")
        println("Main: Now I can quit")
    }
}
