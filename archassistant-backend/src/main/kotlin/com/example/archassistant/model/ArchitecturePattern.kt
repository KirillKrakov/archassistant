package com.example.archassistant.model

/**
 * Распознанный архитектурный паттерн проекта
 * Используется для выбора соответствующего шаблона правил
 */
enum class ArchitecturePattern(
    val description: String,
    val keyLayers: List<String>,
    val typicalAnnotations: List<String>
) {
    CLEAN_ARCHITECTURE(
        description = "Clean Architecture / Hexagonal: независимые слои, инверсия зависимостей",
        keyLayers = listOf("domain", "application", "infrastructure", "interface", "core"),
        typicalAnnotations = listOf("@Entity", "@Service", "@Repository", "@Controller")
    ),

    LAYERED(
        description = "Традиционная слоистая архитектура: presentation → business → data",
        keyLayers = listOf("controller", "service", "repository", "entity", "web", "rest"),
        typicalAnnotations = listOf("@RestController", "@Controller", "@Service", "@Repository", "@Entity")
    ),

    MVVM(
        description = "Model-View-ViewModel: разделение UI и бизнес-логики (Android)",
        keyLayers = listOf("view", "viewmodel", "ui", "screen", "fragment", "activity", "compose"),
        typicalAnnotations = listOf("@ViewModel", "@LiveData", "@Composable")
    ),

    HEXAGONAL(
        description = "Hexagonal / Ports & Adapters: ядро домена изолировано от внешних зависимостей",
        keyLayers = listOf("domain", "application", "port", "ports", "adapter", "adapters"),
        typicalAnnotations = listOf("@Service", "@Repository", "@Configuration")
    ),

    MODULAR(
        description = "Модульная архитектура: независимые модули с чёткими интерфейсами",
        keyLayers = listOf("api", "impl", "common", "shared", "feature", "module"),
        typicalAnnotations = listOf("@Module", "@Provides", "@Inject")
    ),

    UNKNOWN(
        description = "Архитектурный паттерн не распознан или смешанный",
        keyLayers = emptyList(),
        typicalAnnotations = emptyList()
    );

    companion object {
        fun fromLayers(packages: List<String>, annotations: Map<String, Int>): ArchitecturePattern {
            val packageNames = packages.map { it.lowercase() }
            val annotationNames = annotations.keys.map { it.removePrefix("@").lowercase() }

            fun packageScore(keywords: List<String>): Double =
                keywords.count { keyword -> packageNames.any { it.contains(keyword.lowercase()) } }.toDouble()

            fun annotationScore(keywords: List<String>): Double =
                keywords.count { keyword ->
                    val k = keyword.removePrefix("@").lowercase()
                    annotationNames.any { it.contains(k) }
                }.toDouble()

            val scores = entries
                .filter { it != UNKNOWN }
                .associateWith { pattern ->
                    val pkgScore = packageScore(pattern.keyLayers) * when (pattern) {
                        LAYERED -> 2.5
                        CLEAN_ARCHITECTURE -> 2.2
                        HEXAGONAL -> 2.2
                        MVVM -> 2.0
                        MODULAR -> 2.0
                        UNKNOWN -> 1.0
                    }

                    val annScore = annotationScore(pattern.typicalAnnotations) * 1.25

                    val extra = when (pattern) {
                        LAYERED -> {
                            if (
                                annotationNames.any { it.contains("controller") } &&
                                annotationNames.any { it.contains("service") } &&
                                annotationNames.any { it.contains("repository") }
                            ) 3.0 else 0.0
                        }

                        CLEAN_ARCHITECTURE -> {
                            if (
                                packageNames.any { it.contains("domain") } &&
                                packageNames.any { it.contains("application") }
                            ) 2.0 else 0.0
                        }

                        HEXAGONAL -> {
                            if (
                                packageNames.any { it.contains("port") } ||
                                packageNames.any { it.contains("adapter") }
                            ) 2.0 else 0.0
                        }

                        MVVM -> {
                            if (
                                packageNames.any { it.contains("viewmodel") } ||
                                packageNames.any { it.contains("view") } ||
                                packageNames.any { it.contains("ui") }
                            ) 2.0 else 0.0
                        }

                        MODULAR -> {
                            if (
                                packageNames.any { it.contains("feature") } ||
                                packageNames.any { it.contains("impl") } ||
                                packageNames.any { it.contains("api") }
                            ) 1.5 else 0.0
                        }

                        UNKNOWN -> 0.0
                    }

                    pkgScore + annScore + extra
                }

            val best = scores.maxByOrNull { it.value } ?: return UNKNOWN
            return if (best.value <= 0.0) UNKNOWN else best.key
        }
    }
}