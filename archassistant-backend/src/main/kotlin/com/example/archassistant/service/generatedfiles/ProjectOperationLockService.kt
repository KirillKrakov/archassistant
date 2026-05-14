package com.example.archassistant.service.generatedfiles

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ProjectOperationLockService {

    private val locks = ConcurrentHashMap<String, Any>()

    fun <T> withLock(projectId: String, block: () -> T): T {
        val lock = locks.computeIfAbsent(projectId) { Any() }
        synchronized(lock) {
            return block()
        }
    }
}