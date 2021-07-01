package org.unbrokendome.awscodeartifact.mavenproxy.netty.util

import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GlobalEventExecutor
import java.io.Closeable


internal interface AsyncCloseable : Closeable {

    fun closeAsync(): Future<Void?>
}


internal fun Closeable.closeAsyncIfPossible(): Future<Void?> =
    (this as? AsyncCloseable)?.closeAsync()
        ?: try {
            close()
            GlobalEventExecutor.INSTANCE.newSucceededFuture(null)
        } catch (ex: Throwable) {
            GlobalEventExecutor.INSTANCE.newFailedFuture(ex)
        }
