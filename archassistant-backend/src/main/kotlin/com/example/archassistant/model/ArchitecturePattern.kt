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
        keyLayers = listOf("domain", "application", "infrastructure", "interface"),
        typicalAnnotations = listOf("@Entity", "@Service", "@Repository", "@Controller")
    ),

    LAYERED(
        description = "Традиционная слоистая архитектура: presentation → business → data",
        keyLayers = listOf("controller", "service", "repository", "entity"),
        typicalAnnotations = listOf("@RestController", "@Service", "@Repository", "@Entity")
    ),

    MVVM(
        description = "Model-View-ViewModel: разделение UI и бизнес-логики (Android)",
        keyLayers = listOf("view", "viewmodel", "model", "repository"),
        typicalAnnotations = listOf("@ViewModel", "@LiveData", "@Repository", "@Entity")
    ),

    HEXAGONAL(
        description = "Hexagonal / Ports & Adapters: ядро домена изолировано от внешних зависимостей",
        keyLayers = listOf("domain", "application", "ports", "adapters"),
        typicalAnnotations = listOf("@Service", "@Repository", "@Configuration")
    ),

    MODULAR(
        description = "Модульная архитектура: независимые модули с чёткими интерфейсами",
        keyLayers = listOf("api", "impl", "common", "feature"),
        typicalAnnotations = listOf("@Module", "@Provides", "@Inject")
    ),

    UNKNOWN(
        description = "Архитектурный паттерн не распознан или смешанный",
        keyLayers = emptyList(),
        typicalAnnotations = emptyList()
    );

    companion object {
        fun fromLayers(packages: List<String>, annotations: Map<String, Int>): ArchitecturePattern {
            // FIXED: используем contains() вместо точного сравнения
            val packageNames = packages.map { it.lowercase() }
            val annotationNames = annotations.keys.map { it.lowercase() }

            fun hasPackageKeyword(vararg keywords: String): Boolean {
                return packageNames.any { pkg ->
                    keywords.any { keyword -> pkg.contains(keyword) }
                }
            }

            return when {
                // Clean / Hexagonal
                hasPackageKeyword("domain", "application", "infrastructure") &&
                        annotationNames.any { it.contains("entity") } -> CLEAN_ARCHITECTURE

                // Layered (Spring Boot style)
                hasPackageKeyword("controller", "service", "repository") &&
                        annotationNames.any { it.contains("restcontroller") || it.contains("service") } -> LAYERED

                // MVVM (Android)
                hasPackageKeyword("viewmodel", "view", "fragment") ||
                        annotationNames.any { it.contains("viewmodel") || it.contains("livedata") } -> MVVM

                // Hexagonal (ports/adapters)
                hasPackageKeyword("port", "adapter", "spi") -> HEXAGONAL

                // Modular
                hasPackageKeyword("api", "impl", "feature") -> MODULAR

                else -> UNKNOWN
            }
        }
    }
}