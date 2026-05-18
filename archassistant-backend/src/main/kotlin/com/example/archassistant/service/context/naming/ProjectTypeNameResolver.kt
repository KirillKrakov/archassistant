package com.example.archassistant.service.context.naming

import com.example.archassistant.model.context.ClassInfo
import com.example.archassistant.model.context.canonicalName
import com.example.archassistant.model.context.canonicalTypeAliases
import com.example.archassistant.model.context.isPublicType

object ProjectTypeNameResolver {

    fun normalizePackageName(raw: String?): String? =
        raw?.trim()?.trim('.')?.takeIf { it.isNotBlank() }

    fun normalizeTypeName(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }?.replace('$', '.')

    fun normalizeTypeText(text: String?): String =
        text?.replace('$', '.')?.trim().orEmpty()

    fun displayName(info: ClassInfo): String = info.canonicalName

    fun displaySimpleName(info: ClassInfo): String = info.canonicalName.substringAfterLast('.')

    fun typeAliases(raw: String?): Set<String> {
        val canonical = normalizeTypeName(raw) ?: return emptySet()

        val aliases = linkedSetOf<String>()
        val segments = canonical.split('.').filter { it.isNotBlank() }

        aliases += canonical

        if (segments.isNotEmpty()) {
            aliases += segments.last()
            if (segments.size >= 2) {
                aliases += segments.takeLast(2).joinToString(".")
            }
        }

        return aliases.filter { it.isNotBlank() }.toSet()
    }

    fun isVisibleInPrompt(info: ClassInfo, targetPackage: String?): Boolean {
        val pkg = normalizePackageName(targetPackage)
        return info.isPublicType || (pkg != null && info.packageName == pkg)
    }

    fun isAccessibleForImport(info: ClassInfo, currentPackage: String?): Boolean {
        val pkg = normalizePackageName(currentPackage)
        return info.isPublicType || (pkg != null && info.packageName == pkg)
    }

    fun matchesRequestText(info: ClassInfo, requestText: String): Boolean {
        if (requestText.isBlank()) return false

        val request = requestText.lowercase()
        return info.canonicalTypeAliases().any { alias ->
            request.contains(alias.lowercase())
        }
    }

    fun matchesRequestedNames(info: ClassInfo, requestedNames: Collection<String>): Boolean {
        if (requestedNames.isEmpty()) return false

        val infoAliases = info.canonicalTypeAliases()
            .map { it.lowercase() }
            .toSet()

        return requestedNames.any { requested ->
            typeAliases(requested).any { alias ->
                alias.lowercase() in infoAliases
            }
        }
    }

    fun buildUniqueImportIndex(
        classes: List<ClassInfo>,
        currentPackage: String?
    ): Map<String, String> {
        val normalizedPackage = normalizePackageName(currentPackage)
        val buckets = mutableMapOf<String, MutableSet<String>>()

        classes.asSequence()
            .distinctBy { it.canonicalName }
            .forEach { info ->
                if (normalizedPackage != null && info.packageName == normalizedPackage) {
                    return@forEach
                }
                if (!isAccessibleForImport(info, normalizedPackage)) {
                    return@forEach
                }

                val fqcn = info.canonicalName
                info.canonicalTypeAliases().forEach { alias ->
                    if (alias.isNotBlank()) {
                        buckets.getOrPut(alias) { linkedSetOf() }.add(fqcn)
                    }
                }
            }

        return buckets.mapNotNull { (alias, fqcnSet) ->
            if (fqcnSet.size == 1) {
                alias to fqcnSet.first()
            } else {
                null
            }
        }.toMap()
    }
}