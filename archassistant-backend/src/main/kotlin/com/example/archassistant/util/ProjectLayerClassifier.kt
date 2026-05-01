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

    fun matchesClassType(classInfo: ClassInfo, type: ClassType): Boolean {
        return classify(classInfo.packageName, classInfo.simpleName, classInfo.annotations).toClassType() == type
    }

    fun matchesClassType(javaClass: JavaClass, type: ClassType): Boolean {
        return classify(javaClass.packageName, javaClass.name.substringAfterLast('.'),
            javaClass.annotations.map { it.type.name.substringAfterLast('.') }
        ).toClassType() == type
    }

    fun matchesLayer(classInfo: ClassInfo, type: LayerType): Boolean = classify(classInfo) == type

    fun matchesLayer(javaClass: JavaClass, type: LayerType): Boolean = classify(javaClass) == type

    private fun classify(
        packageName: String,
        simpleName: String,
        annotationsRaw: List<String>
    ): LayerType {
        val pkg = packageName.lowercase()
        val simple = simpleName.lowercase()
        val annotations = annotationsRaw.map { it.removePrefix("@").lowercase() }
        val lastSegment = pkg.split('.').lastOrNull().orEmpty()

        return when {
            isController(pkg, lastSegment, simple, annotations) -> LayerType.CONTROLLER
            isViewModel(pkg, lastSegment, simple, annotations) -> LayerType.VIEWMODEL
            isView(pkg, lastSegment, simple, annotations) -> LayerType.VIEW
            isRepository(pkg, lastSegment, simple, annotations) -> LayerType.REPOSITORY
            isService(pkg, lastSegment, simple, annotations) -> LayerType.SERVICE
            isEntity(pkg, lastSegment, simple, annotations) -> LayerType.ENTITY
            isDto(pkg, lastSegment, simple, annotations) -> LayerType.DTO
            isPort(pkg, lastSegment, simple, annotations) -> LayerType.PORT
            isAdapter(pkg, lastSegment, simple, annotations) -> LayerType.ADAPTER
            isApi(pkg, lastSegment, simple, annotations) -> LayerType.API
            isImpl(pkg, lastSegment, simple, annotations) -> LayerType.IMPL
            isFeature(pkg, lastSegment, simple, annotations) -> LayerType.FEATURE
            isCommon(pkg, lastSegment, simple, annotations) -> LayerType.COMMON
            isApplication(pkg, lastSegment, simple, annotations) -> LayerType.APPLICATION
            isInfrastructure(pkg, lastSegment, simple, annotations) -> LayerType.INFRASTRUCTURE
            isDomain(pkg, lastSegment, simple, annotations) -> LayerType.DOMAIN
            else -> LayerType.OTHER
        }
    }

    private fun isController(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "controller" || it == "restcontroller" } ||
                last in setOf("controller", "web", "rest", "resource", "endpoint", "api") ||
                simple.endsWith("controller") ||
                simple.endsWith("resource")
    }

    private fun isViewModel(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it.contains("viewmodel") || it.contains("livedata") } ||
                last in setOf("viewmodel", "vm") ||
                simple.endsWith("viewmodel")
    }

    private fun isView(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "composable" } ||
                last in setOf("view", "ui", "screen", "fragment", "activity") ||
                simple.endsWith("view") ||
                simple.endsWith("fragment") ||
                simple.endsWith("activity") ||
                simple.endsWith("screen")
    }

    private fun isRepository(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "repository" } ||
                last in setOf("repository", "dao", "persistence", "data") ||
                simple.endsWith("repository") ||
                simple.endsWith("dao")
    }

    private fun isService(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "service" } ||
                last in setOf("service", "business", "usecase", "interactor") ||
                simple.endsWith("service") ||
                simple.endsWith("usecase") ||
                simple.endsWith("interactor")
    }

    private fun isEntity(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "entity" || it == "table" } ||
                last in setOf("entity", "model", "domain") ||
                simple.endsWith("entity")
    }

    private fun isDto(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("dto", "vo", "request", "response", "command", "query") ||
                simple.endsWith("dto") ||
                simple.endsWith("request") ||
                simple.endsWith("response") ||
                simple.endsWith("command") ||
                simple.endsWith("query")
    }

    private fun isPort(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("port", "ports", "contract", "gateway", "spi") ||
                simple.endsWith("port")
    }

    private fun isAdapter(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("adapter", "adapters", "impl", "implementation") ||
                simple.endsWith("adapter") ||
                simple.endsWith("impl")
    }

    private fun isApi(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("api", "public", "contract", "exposed") ||
                simple.endsWith("api")
    }

    private fun isImpl(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("impl", "implementation") || simple.endsWith("impl")
    }

    private fun isFeature(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("feature", "module", "modules") || simple.contains("feature")
    }

    private fun isCommon(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("common", "shared", "base", "kernel", "util") || simple.contains("common")
    }

    private fun isApplication(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("application", "usecase", "interactor") ||
                simple.endsWith("usecase") ||
                simple.endsWith("interactor")
    }

    private fun isInfrastructure(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("infrastructure", "persistence", "adapter", "client", "integration", "external")
    }

    private fun isDomain(pkg: String, last: String, simple: String, annotations: List<String>): Boolean {
        return last in setOf("domain", "core")
    }

    private fun LayerType.toClassType(): ClassType? = when (this) {
        LayerType.CONTROLLER -> ClassType.CONTROLLER
        LayerType.SERVICE -> ClassType.SERVICE
        LayerType.REPOSITORY -> ClassType.REPOSITORY
        LayerType.ENTITY -> ClassType.ENTITY
        LayerType.DTO -> ClassType.DTO
        else -> null
    }
}