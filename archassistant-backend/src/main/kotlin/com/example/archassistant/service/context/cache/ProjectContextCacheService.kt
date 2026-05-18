package com.example.archassistant.service.context.cache

import com.example.archassistant.config.ArchassistantProperties
import com.example.archassistant.model.context.ProjectContextSnapshot
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class ProjectContextCacheService(
    private val properties: ArchassistantProperties
) {

    private data class CacheEntry(
        val snapshot: ProjectContextSnapshot,
        val createdAt: Instant
    ) {
        fun isExpired(ttl: Duration): Boolean {
            return Instant.now().isAfter(createdAt.plus(ttl))
        }
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun ttl(): Duration {
        val minutes = properties.cache.projectContextTtlMinutes.coerceAtLeast(1)
        return Duration.ofMinutes(minutes)
    }

    private fun maxEntries(): Int = properties.cache.maxEntries.coerceAtLeast(1)

    fun get(projectId: String): ProjectContextSnapshot? {
        val entry = cache[projectId] ?: return null

        if (entry.isExpired(ttl())) {
            cache.remove(projectId, entry)
            return null
        }

        return entry.snapshot
    }

    @Synchronized
    fun put(projectId: String, snapshot: ProjectContextSnapshot) {
        evictExpiredEntries()

        val currentMax = maxEntries()
        if (!cache.containsKey(projectId) && cache.size >= currentMax) {
            evictOldestEntry()
        }

        cache[projectId] = CacheEntry(snapshot, Instant.now())
    }

    @Synchronized
    fun invalidate(projectId: String) {
        cache.remove(projectId)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    fun size(): Int {
        evictExpiredEntries()
        return cache.size
    }

    @Synchronized
    private fun evictExpiredEntries() {
        val ttl = ttl()
        cache.entries.removeIf { it.value.isExpired(ttl) }
    }

    @Synchronized
    private fun evictOldestEntry() {
        val oldest = cache.entries.minByOrNull { it.value.createdAt } ?: return
        cache.remove(oldest.key)
    }
}