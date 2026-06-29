package com.hackastic.decmed.worker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PghdBatchCreationGuard {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T =
        mutex.withLock { block() }
}
