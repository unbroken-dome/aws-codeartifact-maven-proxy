package org.unbrokendome.awscodeartifact.mavenproxy.netty.pool

import io.netty.channel.pool.ChannelPool
import io.netty.channel.pool.ChannelPoolMap
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GlobalEventExecutor
import io.netty.util.concurrent.ImmediateEventExecutor
import io.netty.util.concurrent.PromiseCombiner
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.AsyncCloseable
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.addListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap



internal interface AsyncCloseAwareChannelPoolMap<K : Any, P : AsyncCloseableChannelPool> :
    ChannelPoolMap<K, P>, AsyncCloseable


internal abstract class AbstractAsyncCloseAwareChannelPoolMap<K : Any, P : AsyncCloseableChannelPool> :
    AsyncCloseAwareChannelPoolMap<K, P>, Iterable<Map.Entry<K, P>> {

    private val map: ConcurrentMap<K, P> = ConcurrentHashMap()


    /**
     * Called once a new pool needs to be created as none exists yet for the key.
     *
     * @param key the key for the new pool
     * @return the new [ChannelPool]
     */
    protected abstract fun newPool(key: K): P


    final override operator fun get(key: K): P =
        map.computeIfAbsent(key, this::newPool)


    /**
     * Removes and closes the pool identified by the given key. The pool will be closed asynchronously.
     *
     * @param key the key that identifies the pool to remove
     * @return `true` if removed, `false` otherwise
     */
    fun remove(key: K): Boolean {
        val pool = map.remove(key) ?: return false
        pool.closeAsync()
        return true
    }


    /**
     * Asynchronously removes and closes the pool identified by the given key.
     *
     * The returned future will be completed once the asynchronous pool close operation completes.
     *
     * @key the key that identifies the pool to remove
     * @return a [Future] with a `true` result if the pool has been removed by this call, or with a `false`
     *         result otherwise
     */
    fun removeAsync(key: K): Future<Boolean> {
        val pool = map.remove(key) ?: return GlobalEventExecutor.INSTANCE.newSucceededFuture(false)

        return GlobalEventExecutor.INSTANCE.newPromise<Boolean>().also { removePromise ->
            pool.closeAsync().addListener(
                onSuccess = { removePromise.setSuccess(true) },
                onError = { error -> removePromise.setFailure(error) }
            )
        }
    }


    final override fun iterator(): Iterator<Map.Entry<K, P>> =
        map.entries.iterator()


    /**
     * The number of pools currently in this pool map.
     */
    val size: Int
        get() = map.size


    /**
     * Returns whether the pool map is empty.
     *
     * @return `true` if there are no channel pools in this map, otherwise `false`
     */
    fun isEmpty(): Boolean =
        map.isEmpty()


    final override fun contains(key: K): Boolean =
        map.containsKey(key)


    final override fun closeAsync(): Future<Void?> {

        val combiner = PromiseCombiner(ImmediateEventExecutor.INSTANCE)
        for (key in map.keys) {
            combiner.add(removeAsync(key))
        }

        return GlobalEventExecutor.INSTANCE.newPromise<Void?>()
            .also { combiner.finish(it) }
    }


    final override fun close() {
        closeAsync().syncUninterruptibly()
    }
}
