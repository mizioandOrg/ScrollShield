package com.scrollshield.feature.mask

import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.SkipDecision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe runtime ScanMap for the current scroll session.
 *
 * All mutable state is guarded by [mutex] so the pre-scan coroutine
 * and the user-scroll handler can safely race.
 */
class ScanMapRuntime(
    val sessionId: String,
    val app: String
) {
    private val mutex = Mutex()

    private val _items = mutableListOf<ClassifiedItem>()
    private val _skipIndices = mutableSetOf<Int>()

    var scanHead: Int = 0
        private set
    var userHead: Int = 0
        private set
    var isExtending: Boolean = false
        private set
    var lastValidatedHash: String? = null
        private set

    val size: Int get() = _items.size

    suspend fun addItem(position: Int, item: ClassifiedItem) = mutex.withLock {
        // Pad list if needed (positions may arrive out of order in edge cases)
        while (_items.size <= position) _items.add(_items.lastOrNull() ?: item)
        _items[position] = item

        if (item.skipDecision != SkipDecision.SHOW &&
            item.skipDecision != SkipDecision.SHOW_LOW_CONF
        ) {
            _skipIndices.add(position)
        }

        if (position >= scanHead) scanHead = position + 1
    }

    suspend fun advanceUserHead(position: Int) = mutex.withLock {
        userHead = position
    }

    suspend fun setExtending(extending: Boolean) = mutex.withLock {
        isExtending = extending
    }

    suspend fun setLastValidatedHash(hash: String?) = mutex.withLock {
        lastValidatedHash = hash
    }

    suspend fun shouldSkip(position: Int): Boolean = mutex.withLock {
        position in _skipIndices
    }

    suspend fun getItem(position: Int): ClassifiedItem? = mutex.withLock {
        _items.getOrNull(position)
    }

    suspend fun getSkipIndices(): Set<Int> = mutex.withLock {
        _skipIndices.toSet()
    }

    /** How far ahead the scan is from the user's current position. */
    suspend fun bufferRemaining(): Int = mutex.withLock {
        scanHead - userHead
    }

    /** Check if a given position has been pre-scanned. */
    suspend fun isScanned(position: Int): Boolean = mutex.withLock {
        position < scanHead && position < _items.size
    }

    /** Detect duplicate items (back-stack limit reached). */
    suspend fun isDuplicate(item: ClassifiedItem): Boolean = mutex.withLock {
        _items.any { it.feedItem.id == item.feedItem.id }
    }

    suspend fun clear() = mutex.withLock {
        _items.clear()
        _skipIndices.clear()
        scanHead = 0
        userHead = 0
        isExtending = false
        lastValidatedHash = null
    }

    suspend fun snapshot(): List<ClassifiedItem> = mutex.withLock {
        _items.toList()
    }
}
