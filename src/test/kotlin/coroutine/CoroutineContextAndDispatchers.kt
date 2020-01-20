package coroutine

import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.Test

class CoroutineContextAndDispatchers {

  private val logger: Logger = LogManager.getLogger(CoroutineContextAndDispatchers::class.java)

  @ObsoleteCoroutinesApi
  @Test
  fun `The coroutine context includes a coroutine dispatcher that determines what thread or threads the corresponding coroutine uses for its execution`() = runBlocking<Unit> {
    launch {
      println("Main runBlocking: I'm running in the thread '${Thread.currentThread().name}'")
    }
    launch(Dispatchers.Unconfined) {
      println("Unconfined: I'm running in thread '${Thread.currentThread().name}'")
    }
    launch(Dispatchers.Default) {
      println("Default: I'm running in thread '${Thread.currentThread().name}'")
    }
    launch(newSingleThreadContext("MyOwnThread")) {
      println("newSingleThreadContext: I'm working in the thread '${Thread.currentThread().name}'")
    }
  }

  @Test
  fun `The dispatcher is inherited from the outer CoroutineScope by default, for runBlocking, in particular, is confined to the invoker thread`() = runBlocking<Unit> {
    launch(Dispatchers.Unconfined) {
      println("Unconfined: I'm working in thread '${Thread.currentThread().name}'")
      delay(500)
      println("Unconfined: After delay in thread '${Thread.currentThread().name}'")
      println("""
        Unconfined comment: unconfined coroutine resumes in the default executor thread that the
        delay function is using.
      """.trimIndent())
      println("""
        The unconfined dispatcher is an advanced mechanism that can be helpful in certain corner
        cases where dispatching of a coroutine for its execution later is not needed or produces
        undesirable side-effects, because some operation in a coroutine must be performed right
        away. The unconfined dispatcher should not be used in general code.
      """.trimIndent())
      println("")
    }
    launch {
      println("Main runBlocking: I'm working in thread '${Thread.currentThread().name}'")
      delay(1000)
      println("Main runBlocking: After delay in thread ${Thread.currentThread().name}")
    }
  }

  @Test
  fun `Debugging mode for coroutines is set by VM option -Dkotlinx_coroutines_debug or -ea`() = runBlocking {
    val a = async {
      logger.info("I'm computing a piece of the answer")
      6
    }
    val b = async {
      logger.info("I'm computing another piece of the answer")
      7
    }
    logger.info("The answer is ${a.await() + b.await()}")
  }

  @ObsoleteCoroutinesApi
  @Test
  fun `Jumping between threads`() = runBlocking {
    newSingleThreadContext("Ctx1").use { ctx1 ->
      newSingleThreadContext("Ctx2").use { ctx2 ->
        runBlocking(ctx1) {
          logger.info("Started in ctx1 ${coroutineContext[Job]}")
          withContext(ctx2) {
            logger.info("Working in Ctx2 ${coroutineContext[Job]}")
          }
          logger.info("Back to ctx1 ${coroutineContext[Job]}")
        }
      }
    }
  }

  @Test
  fun `Coroutines launched in the CoroutineScope of another coroutine inherit its context however GlobalScope coroutines operate independently`() = runBlocking {
    val request = launch {
      GlobalScope.launch {
        println("Job1: I run in GlobalScope and execute independently")
        delay(1000)
        println("Job1: I am not affected by cancellation of the request")
      }
      launch {
        delay(100)
        println("Job2: I'm a child of the request coroutine")
        delay(1000)
        println("Job2: I will not execute this line of my parent request is cancelled")
      }
    }
    delay(500)
    request.cancel()
    delay(1000)
    println("Main: Who has survived request cancellation?")
  }

  @Test
  fun `Parent coroutine always wait for completion of all its children without having to join and wait for them explicitly`() = runBlocking {
    val request = launch {
      repeat(3) { i ->
        launch {
          delay((i + 1) * 200L)
          println("Coroutine $i is done")
        }
      }
      println("Request: I'm done and I don't explicitly join my children that are still active")
    }
    request.join()
    println("Now processing of the request is complete")
  }

  @Test
  fun `use CoroutineName to name coroutine explicitly for debugging purpose`() = runBlocking {
    logger.info("Started main coroutine")
    val v1 = async(CoroutineName("v1coroutine")) {
      delay(500)
      logger.info("Computing v1")
      252
    }
    val v2 = async(CoroutineName("v2coroutine")) {
      delay(1000)
      logger.info("Computing v2")
      6
    }
    logger.info("The answer for v1/v2 = ${v1.await() / v2.await()}")
  }

  @Test
  fun `Use + operator to combine context elements such as dispatcher and name`() = runBlocking<Unit> {
    launch(Dispatchers.Default + CoroutineName("Test")) {
      println("I'm working in thread ${Thread.currentThread().name}")
    }
  }
}
