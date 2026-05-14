package com.example.archassistant.service.context

import com.example.archassistant.model.ProjectContextSnapshot
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class ProjectContextCacheService {

    private data class CacheEntry(
        val snapshot: ProjectContextSnapshot,
        val createdAt: Instant
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(projectId: String): ProjectContextSnapshot? = cache[projectId]?.snapshot

    fun put(projectId: String, snapshot: ProjectContextSnapshot) {
        cache[projectId] = CacheEntry(snapshot, Instant.now())
    }

    fun invalidate(projectId: String) {
        cache.remove(projectId)
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size
}