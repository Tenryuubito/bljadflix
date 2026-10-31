package com.shadow.firetv.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class ProgressSync(private val scope: CoroutineScope) {

    private val pending = mutableSetOf<Job>()

    fun launch(block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        synchronized(pending) { pending.add(job) }
        job.invokeOnCompletion { synchronized(pending) { pending.remove(job) } }
        job.start()
    }

    suspend fun awaitPending() {
        val jobs = synchronized(pending) { pending.toList() }
        jobs.joinAll()
    }

    companion object {
        val shared: ProgressSync by lazy { ProgressSync(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    }
}
