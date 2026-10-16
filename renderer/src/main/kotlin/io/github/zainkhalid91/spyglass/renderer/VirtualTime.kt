@file:OptIn(InternalCoroutinesApi::class)

package io.github.zainkhalid91.spyglass.renderer

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.PriorityQueue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * The coroutine context previews run in. Work runs immediately, like `Dispatchers.Unconfined`, but
 * `delay()` waits on a virtual clock that the renderer moves forward together with the frame time.
 * A preview that loads fake data after `delay(500)` therefore shows the loaded state, without the
 * render waiting half a second.
 */
internal class VirtualTime : CoroutineDispatcher(), Delay {
    private class Wakeup(val atNanos: Long, val order: Long, val continuation: CancellableContinuation<Unit>)

    private val pending = PriorityQueue<Wakeup>(compareBy<Wakeup>({ it.atNanos }, { it.order }))
    private var order = 0L

    var nowNanos = 0L
        private set

    // Run inline, as Unconfined does; there is only one thread here.
    override fun isDispatchNeeded(context: CoroutineContext) = false

    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        pending += Wakeup(nowNanos + timeMillis.coerceIn(0, 1_000_000_000L) * 1_000_000, order++, continuation)
    }

    /** When the next `delay()` still being waited on ends, or null if none is. */
    fun nextWakeup(): Long? = pending.filter { it.continuation.isActive }.minOfOrNull { it.atNanos }

    /** Moves the clock to [nanos], resuming every coroutine whose delay ends by then, in order. */
    fun advanceTo(nanos: Long) {
        while (true) {
            val next = pending.peek() ?: break
            if (next.atNanos > nanos) break
            pending.poll()
            nowNanos = maxOf(nowNanos, next.atNanos)
            if (next.continuation.isActive) next.continuation.resume(Unit)
        }
        nowNanos = maxOf(nowNanos, nanos)
    }
}
