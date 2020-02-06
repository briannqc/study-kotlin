package coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class AsynchronousFlow {

    private val logger: Logger = LogManager.getLogger(javaClass)

    private fun foo(): Flow<Int> = flow {
        logger.info("Flow started")
        for (i in 1..3) {
            delay(100)
            logger.info("Emitting $i")
            emit(i)
        }
    }

    @Test
    fun `Similar to Sequence for sync, Flow represents stream of values that are being asynchronously computed`() = runBlocking {
        launch {
            for (k in 1..3) {
                println("I'm not blocked $k")
                delay(100)
            }
        }
        foo().collect { println(it) }
    }

    @Test
    fun `Flows are cold, the code inside a flow builder does not run until the flow is collected`() = runBlocking {
        println("Calling foo...")
        val flow = foo()
        println("Calling collect ...")
        flow.collect { value -> println(value)}
        println("Calling collect again...")
        flow.collect { value -> println(value)}
    }

    @Test
    fun `Flow collection can be cancelled when the flow is suspended in a cancellable suspending function and cannot be cancelled otherwise`() = runBlocking {
        withTimeoutOrNull(250) {
            foo().collect { value -> println(value) }
        }
        println("Done")
    }

    @Test
    fun `Besides flow(), flowOf() builder and asFlow() extension functions can be used to produce flow as well`() = runBlocking {
        (1..3).asFlow().collect { value -> println(value) }
        flowOf(1, 2, 3).collect { value -> println(value) }
    }

    private suspend fun performRequest(request: Int): String {
        delay(1000)
        return "Response $request"
    }

    @Test
    fun `Intermediate flow operators are cold just like flows are`() = runBlocking {
        (1..3).asFlow()
            .map { request -> performRequest(request) }
            .collect { response -> println(response) }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `transform() is the most general flow transformation operators`() = runBlocking {
        (1..3).asFlow()
            .transform { request ->
                emit("Making request $request")
                emit(performRequest(request))
            }
            .collect { response -> println(response) }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Size-limiting intermediate operations like take cancel the execution of the flow when the coresponding limit is reachec`() = runBlocking {
        val numbersFlow = flow {
            try {
                emit(1)
                emit(2)
                println("This line will not execute")
                emit(3)
            } finally {
                println("Finally is numbersFlow")
            }
        }
        numbersFlow.take(2)
            .collect { println(it) }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `collect(), toList(), toSet(), first(), reduce() an fold() are terminal flow operators`() = runBlocking {
        val sum = (1..5).asFlow()
            .map { it * it }
            .reduce { a, b -> a + b }
        println(sum)
    }

    @Test
    fun `Flows are sequential`() = runBlocking {
        (1..5).asFlow()
            .filter {
                println("Filter $it")
                it % 2 == 0
            }
            .map {
                println("Map $it")
                "String $it"
            }
            .collect {
                println("Collect $it")
            }
    }

    @Test
    fun `Flow context - Collection of a flow always happens in the context of the calling coroutine`() = runBlocking {
        foo().collect { value -> logger.info("Collected $value") }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Use flowOn function to change the context of a flow`() = runBlocking {
        foo().flowOn(Dispatchers.Default).collect { value -> logger.info("Collected $value") }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Use buffer operator on a flow to run emitting code concurrently with collecting code`() = runBlocking {
        val timeWithoutBuffer = measureTimeMillis {
            foo()
                .collect { value ->
                    delay(300)
                    println(value)
                }
        }
        val timeWithBuffer = measureTimeMillis {
            foo()
                .buffer()
                .collect { value ->
                    delay(300)
                    println(value)
                }
        }
        println("timeWithoutBuffer $timeWithoutBuffer")
        println("timeWithBuffer $timeWithBuffer")
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Use conflate operator to skip intermediate values when a collector is too slow to process them`() = runBlocking {
        val time = measureTimeMillis {
            foo()
                .conflate()
                .collect { value ->
                    logger.info("Processing $value")
                    delay(300)
                    logger.info("Processed $value")
                }
        }
        logger.info("Collected in $time ms")
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Use xxxLatest operation to cancel a slow collector and restart it very time a new value is emitted`() = runBlocking {
        val time = measureTimeMillis {
            foo()
                .collectLatest { value ->
                    logger.info("Processing $value")
                    delay(300)
                    logger.info("Processed $value")
                }
        }
        logger.info("Collected in $time ms")
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Use zip or combine to compose multiple flows`() = runBlocking {
        val nums = (1..3).asFlow().onEach { delay(300) }
        val strings = flowOf("one", "two", "three").onEach { delay(400) }

        val startTimeZip = System.currentTimeMillis()
        nums.zip(strings) { a, b -> "$a -> $b" }
            .collect { value ->
                logger.info("$value at ${System.currentTimeMillis() - startTimeZip}")
            }

        val startTimeCombine = System.currentTimeMillis()
        nums.combine(strings) { a, b -> "$a -> $b" }
            .collect { value ->
                logger.info("$value at ${System.currentTimeMillis() - startTimeCombine}")
            }
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @Test
    fun `Flattening flows with flatMapConcat, flatMapMerge or flatMapLatest`() = runBlocking {
        fun requestFlow(i: Int): Flow<String> = flow {
            emit("$i: First")
            delay(500)
            emit("$i: Second")
        }

        val startFlatMapConcat = System.currentTimeMillis()
        println("flatMapConcat waits for inner flow to complete before starting to collect the next one")
        (1..3).asFlow().onEach { delay(100) }
            .flatMapConcat { requestFlow(it) }
            .collect { value ->
                logger.info("$value at ${System.currentTimeMillis() - startFlatMapConcat} ms from start")
            }
        println()

        val startFlatMapMerge = System.currentTimeMillis()
        println("flatMapMerge concurrently collect all the incoming flows and merge their values into single flow" +
            " so that values are emitted as soon as possible")
        (1..3).asFlow().onEach { delay(100) }
            .flatMapMerge { requestFlow(it) }
            .collect { value ->
                logger.info("$value at ${System.currentTimeMillis() - startFlatMapMerge} ms from start")
            }
        println()

        val startFlatMapLatest = System.currentTimeMillis()
        println("flatMapLatest cancels collection of the previous flow as soon as new flow is emitted")
        (1..3).asFlow().onEach { delay(100) }
            .flatMapLatest { requestFlow(it) }
            .collect { value ->
                logger.info("$value at ${System.currentTimeMillis() - startFlatMapLatest} ms from start")
            }
        println()
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Handle exception with try-catch or catch intermediate operation`() = runBlocking {
        try {
            foo()
                .map { value ->
                    check(value <= 1) { "Crashed on $value" }
                    "String $value"
                }
                .collect { value -> println(value) }
        } catch (ex: Exception) {
            logger.error(ex)
        }

        foo()
            .onEach { value ->
                check(value <= 1) { "Collected $value"}
            }
            .catch { e -> logger.error("Caught", e) }
            .collect()
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Flow completion`() = runBlocking {
        println("Handle flow completion with Imperative finally block")
        try {
          foo().collect { println(it) }
        } finally {
          logger.info("Done")
        }

        println("Or, Handle flow completion with Declarative handling")
        foo()
            .map { value ->
                check(value <= 1) { "Crashed on $value" }
                "String $value"
            }
            .onCompletion { cause -> if (cause != null) logger.warn("Flow completed exceptionally") }
            .catch { cause -> logger.error(cause) }
            .collect { value -> println(value) }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `Use launchIn terminal operator to launch a collection of the flow in a separate coroutine`() = runBlocking {
        foo()
            .onEach { value -> logger.info(value) }
            .launchIn(this)
        logger.info("Done")
    }
}
