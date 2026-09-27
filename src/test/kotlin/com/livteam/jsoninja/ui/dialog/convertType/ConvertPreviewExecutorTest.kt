package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class ConvertPreviewExecutorTest : BasePlatformTestCase() {
    private data class Preview(val text: String, val warnings: List<String>)

    fun testCancelledSlowRequestCannotReplaceNewTextOrWarnings() {
        val failures = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> failures += error })
        val executor = ConvertPreviewExecutor(scope)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val results = mutableListOf<Preview>()
        val errors = mutableListOf<Throwable>()
        try {
            executor.submitDetailed(0, { assertTrue(SwingUtilities.isEventDispatchThread()) }, {
                assertFalse(SwingUtilities.isEventDispatchThread())
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                Preview("old", listOf("old warning"))
            }, { results += it }, { errors += it })
            awaitCondition { started.count == 0L }
            executor.submitDetailed(0, {}, { Preview("new", listOf("new warning")) }, {
                assertTrue(SwingUtilities.isEventDispatchThread())
                results += it
            }, { errors += it })
            awaitCondition { results.isNotEmpty() }
            release.countDown()
            awaitCondition { scope.coroutineContext[Job]!!.children.none { it.isActive } }
            assertEquals(listOf(Preview("new", listOf("new warning"))), results)
            assertTrue(errors.toString(), errors.isEmpty())
            assertTrue(failures.toString(), failures.isEmpty())
        } finally { release.countDown(); executor.dispose(); scope.cancel() }
    }

    fun testCancellationSignalsNeverBecomePreviewErrorsAndFailuresStayOnEdt() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> uncaught += error })
        val executor = ConvertPreviewExecutor(scope)
        val errors = mutableListOf<Throwable>()
        try {
            for (signal in listOf(CancellationException("cancelled"), ProcessCanceledException())) {
                val started = CountDownLatch(1)
                executor.submit(0, {}, { started.countDown(); throw signal }, { fail("cancelled result applied") }, { errors += it })
                awaitCondition { started.count == 0L && scope.coroutineContext[Job]!!.children.none { it.isActive } }
            }
            assertTrue(errors.toString(), errors.isEmpty())
            assertTrue(uncaught.all { it is ProcessCanceledException })
            val failure = IllegalStateException("conversion failed")
            executor.submit(0, {}, { throw failure }, { fail("failed result applied") }, {
                assertTrue(SwingUtilities.isEventDispatchThread()); errors += it
            })
            awaitCondition { errors.isNotEmpty() }
            val delivered = errors.single()
            assertEquals(failure.javaClass, delivered.javaClass)
            assertEquals(failure.message, delivered.message)
            assertTrue("Original cause must survive coroutine stack recovery", generateSequence(delivered) { it.cause }.any { it === failure })
        } finally { executor.dispose(); scope.cancel() }
    }

    fun testDisposeCancelsDebouncedWorkBeforeItTouchesTheView() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = ConvertPreviewExecutor(scope)
        val calls = CopyOnWriteArrayList<String>()
        try {
            executor.submit(500, { calls += "loading" }, { calls += "compute"; "value" }, { calls += "success" }, { calls += "error" })
            executor.dispose()
            awaitCondition { scope.coroutineContext[Job]!!.children.none { it.isActive } }
            assertTrue(calls.toString(), calls.isEmpty())
        } finally { executor.dispose(); scope.cancel() }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            UIUtil.dispatchAllInvocationEvents()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Preview did not settle before the deadline", condition())
    }
}
