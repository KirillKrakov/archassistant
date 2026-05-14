package com.example.archassistant.model.context

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
    )
}

fun ProjectProfile.toArchitecturePattern(): ArchitecturePattern {
    return when (this) {
        ProjectProfile.SPRING_LAYERED,
        ProjectProfile.SPRING_FEATURED -> ArchitecturePattern.LAYERED

        ProjectProfile.CLEAN -> ArchitecturePattern.CLEAN_ARCHITECTURE
        ProjectProfile.HEXAGONAL -> ArchitecturePattern.HEXAGONAL
        ProjectProfile.MVVM -> ArchitecturePattern.MVVM
        ProjectProfile.MODULAR -> ArchitecturePattern.MODULAR
        ProjectProfile.UNKNOWN -> ArchitecturePattern.UNKNOWN
    }
}