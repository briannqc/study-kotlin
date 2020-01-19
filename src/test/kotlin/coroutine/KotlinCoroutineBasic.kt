package coroutine

import kotlinx.coroutines.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class KotlinCoroutineBasic {

    @Test
    fun `First coroutine`() {
        GlobalScope.launch {
            delay(1000L)
            println("World")
        }
        println("Hello")
        Thread.sleep(2000L)
    }

    @Test
    fun `Bridging blocking and non-blocking worlds, The main thread invoking runBlocking blocks until the coroutine inside runBlocking completes`() = runBlocking {
        GlobalScope.launch {
            delay(1000L)
            println("World")
        }
        println("Hello")
        delay(2000L)
    }

    @Test
    fun `Waiting for a job to complete with join()`() {
        GlobalScope.launch {
            val job = GlobalScope.launch {
                delay(1000L)
                println("World")
            }
            println("Hello")
            job.join() // wait until child coroutine completes
        }
    }

    @Test
    fun `Structured concurrency`() = runBlocking {
        println("""
            Even though GlobalScope.launch is light-weight, it still consumes some memory resources while it runs.
            Better solution: launch coroutines in the specific scope of the operation we are performing.
            Every coroutine builder, including runBlocking, adds an instance of CoroutineScope to the scope of its code block.
        """.trimIndent()
        )
        launch {
            delay(2000L)
            println("Hi I'm here, just come back from 2secs delay")
        }
        launch {
            delay(500L)
            println("World")
        }
        println("Hello")
        delay(1000L)

        println("I'm waiting for all my child coroutines complete, you don't need to join them explicitly")
    }

    @Test
    fun `Scope Builder`() = runBlocking {
        launch {
            delay(200L)
            println("Task from runBlocking")
        }

        coroutineScope {
            println("Unlike runBlocking, I don't block the current thread for waiting," +
                    " I just suspends, releasing the underlying thread for other usages")
            launch {
                delay(500L)
                println("Task from nested launch")
            }

            delay(100)
            println("Task from coroutine scope")
        }

        println("Coroutine scope is over")
    }

    @Test
    fun `First suspend function`() = runBlocking {
        launch {
            doWorld()
        }
        println("Hello")
    }

    private suspend fun doWorld() {
        delay(1000L)
        println("World")
    }

    @Test
    fun `Coroutines ARE light-weight`() = runBlocking {
        repeat(100_000) {
           launch {
               delay(1000L)
               print(".")
           }
        }
    }

    @Test
    @Disabled
    fun `Thread ARE much more expensive than coroutines`() = runBlocking {
        /*
        WARNING: TestEngine with ID 'junit-jupiter' failed to execute tests
        java.lang.OutOfMemoryError: unable to create new native thread
            at java.lang.Thread.start0(Native Method)
            at java.lang.Thread.start(Thread.java:717)
            at kotlin.concurrent.ThreadsKt.thread(Thread.kt:42)
            at kotlin.concurrent.ThreadsKt.thread$default(Thread.kt:25)
         */
        repeat(10_000) {
            thread {
                Thread.sleep(1000)
                print(".")
            }
        }
    }

    @Test
    fun `Global coroutines are like daemon threads Active coroutines that were launched in GlobalScope do not keep the process alive`() = runBlocking {
        GlobalScope.launch {
            repeat(1000) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        }
        delay(1300L)
    }
}
