package com.example.archassistant.util

import com.example.archassistant.model.ClassInfo
import com.example.archassistant.model.ClassType
import com.example.archassistant.model.LayerType
import com.tngtech.archunit.core.domain.JavaClass

object ProjectLayerClassifier {

    fun classify(classInfo: ClassInfo): LayerType {
        return classify(classInfo.packageName, classInfo.simpleName, classInfo.annotations)
    }

    fun classify(javaClass: JavaClass): LayerType {
        val annotations = javaClass.annotations.map { it.type.name.substringAfterLast('.') }
        val simpleName = javaClass.name.substringAfterLast('.')
        return classify(javaClass.packageName, simpleName, annotations)
    }

    fun matchesClassType(javaClass: JavaClass, type: ClassType): Boolean {
        val pkg = javaClass.packageName.lowercase()
        val simple = javaClass.name.substringAfterLast('.').lowercase()
        val annotations = javaClass.annotations.map { it.type.name.substringAfterLast('.').lowercase() }

        return when (type) {
            ClassType.CONTROLLER ->
                pkg.contains("controller") ||
                        pkg.contains("web") ||
                        pkg.contains("api") ||
                        pkg.contains("rest") ||
                        pkg.contains("resource") ||
                        simple.endsWith("controller") ||
                        simple.endsWith("resource") ||
                        annotations.any { it == "controller" || it == "restcontroller" }

            ClassType.SERVICE ->
                pkg.contains("service") ||
                        pkg.contains("business") ||
                        simple.endsWith("service") ||
                        simple.endsWith("usecase") ||
                        simple.endsWith("interactor") ||
                        annotations.any { it == "service" }

            ClassType.REPOSITORY ->
                pkg.contains("repository") ||
                        pkg.contains("dao") ||
                        pkg.contains("persistence") ||
                        pkg.contains("data") ||
                        simple.endsWith("repository") ||
                        simple.endsWith("dao") ||
                        annotations.any { it == "repository" }

            ClassType.ENTITY ->
                pkg.contains("entity") ||
                        pkg.contains("domain") ||
                        pkg.contains("model") ||
                        simple.endsWith("entity") ||
                        annotations.any { it == "entity" || it == "table" }

            ClassType.DTO ->
                pkg.contains("dto") ||
                        pkg.contains("vo") ||
                        pkg.contains("request") ||
                        pkg.contains("response") ||
                        pkg.contains("command") ||
                        pkg.contains("query") ||
                        simple.endsWith("dto") ||
                        simple.endsWith("request") ||
                        simple.endsWith("response") ||
                        simple.endsWith("command") ||
                        simple.endsWith("query")

            ClassType.OTHER -> false
        }
    }

    private fun classify(
        packageName: String,
        simpleName: String,
        annotationsRaw: List<String>
    ): LayerType {
        val pkg = packageName.lowercase()
        val simple = simpleName.lowercase()
        val annotations = annotationsRaw.map { it.removePrefix("@").lowercase() }

        return when {
            isController(pkg, simple, annotations) -> LayerType.CONTROLLER
            isViewModel(pkg, simple, annotations) -> LayerType.VIEWMODEL
            isView(pkg, simple, annotations) -> LayerType.VIEW
            isRepository(pkg, simple, annotations) -> LayerType.REPOSITORY
            isService(pkg, simple, annotations) -> LayerType.SERVICE
            isEntity(pkg, simple, annotations) -> LayerType.ENTITY
            isDto(pkg, simple, annotations) -> LayerType.DTO
            isPort(pkg, simple, annotations) -> LayerType.PORT
            isAdapter(pkg, simple, annotations) -> LayerType.ADAPTER
            isApi(pkg, simple, annotations) -> LayerType.API
            isImpl(pkg, simple, annotations) -> LayerType.IMPL
            isFeature(pkg, simple, annotations) -> LayerType.FEATURE
            isCommon(pkg, simple, annotations) -> LayerType.COMMON
            isApplication(pkg, simple, annotations) -> LayerType.APPLICATION
            isInfrastructure(pkg, simple, annotations) -> LayerType.INFRASTRUCTURE
            isDomain(pkg, simple, annotations) -> LayerType.DOMAIN
            else -> LayerType.OTHER
        }
    }

    private fun isController(pkg: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "controller" || it == "restcontroller" } ||
                pkg.contains("controller") ||
                pkg.contains("web") ||
                pkg.contains("rest") ||
                pkg.contains("resource") ||
                pkg.contains("endpoint") ||
                simple.endsWith("controller") ||
                simple.endsWith("resource")
    }

    private fun isViewModel(pkg: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it.contains("viewmodel") || it.contains("livedata") } ||
                pkg.contains("viewmodel") ||
                pkg.contains("vm") ||
                simple.endsWith("viewmodel")
    }

    private fun isView(pkg: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "composable" } ||
                pkg.contains("view") ||
                pkg.contains("ui") ||
                pkg.contains("screen") ||
                pkg.contains("fragment") ||
                pkg.contains("activity") ||
                simple.endsWith("view") ||
                simple.endsWith("fragment") ||
                simple.endsWith("activity") ||
                simple.endsWith("screen")
    }

    private fun isRepository(pkg: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "repository" } ||
                pkg.contains("repository") ||
                pkg.contains("dao") ||
                pkg.contains("persistence") ||
                pkg.contains("data") ||
                simple.endsWith("repository") ||
                simple.endsWith("dao")
    }

    private fun isService(pkg: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "service" } ||
                pkg.contains("service") ||
                pkg.contains("business") ||
                pkg.contains("usecase") ||
                pkg.contains("interactor") ||
                simple.endsWith("service") ||
                simple.endsWith("usecase") ||
                simple.endsWith("interactor")
    }

    private fun isEntity(pkg: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "entity" || it == "table" } ||
                pkg.contains("entity") ||
                pkg.contains("domain") ||
                pkg.contains("model") ||
                simple.endsWith("entity")
    }

    private fun isDto(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("dto") ||
                pkg.contains("vo") ||
                pkg.contains("request") ||
                pkg.contains("response") ||
                pkg.contains("command") ||
                pkg.contains("query") ||
                simple.endsWith("dto") ||
                simple.endsWith("request") ||
                simple.endsWith("response") ||
                simple.endsWith("command") ||
                simple.endsWith("query")
    }

    private fun isPort(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("port") ||
                pkg.contains("ports") ||
                pkg.contains("contract") ||
                pkg.contains("gateway") ||
                pkg.contains("spi") ||
                simple.endsWith("port")
    }

    private fun isAdapter(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("adapter") ||
                pkg.contains("adapters") ||
                pkg.contains("impl") ||
                pkg.contains("implementation") ||
                simple.endsWith("adapter") ||
                simple.endsWith("impl")
    }

    private fun isApi(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("api") ||
                pkg.contains("public") ||
                pkg.contains("contract") ||
                pkg.contains("exposed") ||
                simple.endsWith("api")
    }

    private fun isImpl(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("impl") ||
                pkg.contains("implementation") ||
                simple.endsWith("impl")
    }

    private fun isFeature(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("feature") ||
                pkg.contains("module") ||
                simple.contains("feature")
    }

    private fun isCommon(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("common") ||
                pkg.contains("shared") ||
                pkg.contains("base") ||
                pkg.contains("kernel") ||
                pkg.contains("util") ||
                simple.contains("common")
    }

    private fun isApplication(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("application") ||
                pkg.contains("usecase") ||
                pkg.contains("interactor") ||
                simple.endsWith("usecase") ||
                simple.endsWith("interactor")
    }

    private fun isInfrastructure(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("infrastructure") ||
                pkg.contains("persistence") ||
                pkg.contains("adapter") ||
                pkg.contains("client") ||
                pkg.contains("integration") ||
                pkg.contains("external")
    }

    private fun isDomain(pkg: String, simple: String, annotations: List<String>): Boolean {
        return pkg.contains("domain") ||
                pkg.contains("core")
    }
}