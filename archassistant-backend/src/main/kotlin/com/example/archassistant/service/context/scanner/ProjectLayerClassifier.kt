package com.example.archassistant.service.context.scanner

import com.example.archassistant.model.context.ClassInfo
import com.example.archassistant.model.context.ClassType
import com.example.archassistant.model.context.LayerType
import com.tngtech.archunit.core.domain.JavaClass

object ProjectLayerClassifier {

    fun classify(classInfo: ClassInfo): LayerType {
        return classify(
            packageName = classInfo.packageName,
            simpleName = classInfo.simpleName,
            annotationsRaw = classInfo.annotations,
            javaClass = null
        )
    }

    fun classify(javaClass: JavaClass): LayerType {
        val annotations = javaClass.annotations.map { it.type.name.substringAfterLast('.') }
        val simpleName = javaClass.name.substringAfterLast('.')
        return classify(
            packageName = javaClass.packageName,
            simpleName = simpleName,
            annotationsRaw = annotations,
            javaClass = javaClass
        )
    }

    fun matchesClassType(classInfo: ClassInfo, type: ClassType): Boolean {
        return classify(classInfo).toClassType() == type
    }

    fun matchesClassType(javaClass: JavaClass, type: ClassType): Boolean {
        return classify(javaClass).toClassType() == type
    }

    fun matchesLayer(classInfo: ClassInfo, type: LayerType): Boolean = classify(classInfo) == type

    fun matchesLayer(javaClass: JavaClass, type: LayerType): Boolean = classify(javaClass) == type

    private fun classify(
        packageName: String,
        simpleName: String,
        annotationsRaw: List<String>,
        javaClass: JavaClass?
    ): LayerType {
        val pkg = packageName.lowercase()
        val simple = simpleName.lowercase()
        val annotations = annotationsRaw.map { it.removePrefix("@").lowercase() }
        val lastSegment = pkg.split('.').lastOrNull().orEmpty()

        return when {
            isController(lastSegment, simple, annotations) -> LayerType.CONTROLLER
            isViewModel(lastSegment, simple, annotations) -> LayerType.VIEWMODEL
            isView(lastSegment, simple, annotations) -> LayerType.VIEW
            isRepository(javaClass, lastSegment, simple, annotations) -> LayerType.REPOSITORY
            isService(lastSegment, simple, annotations) -> LayerType.SERVICE
            isEntity(lastSegment, simple, annotations) -> LayerType.ENTITY
            isDto(lastSegment, simple) -> LayerType.DTO
            isPort(lastSegment, simple) -> LayerType.PORT
            isAdapter(lastSegment, simple) -> LayerType.ADAPTER
            isApi(lastSegment, simple) -> LayerType.API
            isImpl(lastSegment, simple) -> LayerType.IMPL
            isFeature(lastSegment, simple) -> LayerType.FEATURE
            isCommon(lastSegment, simple) -> LayerType.COMMON
            isApplication(lastSegment, simple) -> LayerType.APPLICATION
            isInfrastructure(lastSegment) -> LayerType.INFRASTRUCTURE
            isDomain(lastSegment) -> LayerType.DOMAIN
            else -> LayerType.OTHER
        }
    }

    private fun isController(last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "controller" || it == "restcontroller" } ||
                last in setOf("controller", "web", "rest", "resource", "endpoint", "api") ||
                simple.endsWith("controller") ||
                simple.endsWith("resource")
    }

    private fun isViewModel(last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it.contains("viewmodel") || it.contains("livedata") } ||
                last in setOf("viewmodel", "vm") ||
                simple.endsWith("viewmodel")
    }

    private fun isView(last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "composable" } ||
                last in setOf("view", "ui", "screen", "fragment", "activity") ||
                simple.endsWith("view") ||
                simple.endsWith("fragment") ||
                simple.endsWith("activity") ||
                simple.endsWith("screen")
    }

    private fun isRepository(
        javaClass: JavaClass?,
        last: String,
        simple: String,
        annotations: List<String>
    ): Boolean {
        return annotations.any { it == "repository" } ||
                last in setOf("repository", "dao", "persistence", "data") ||
                simple.endsWith("repository") ||
                simple.endsWith("dao") ||
                javaClass?.let { hasRepositoryContract(it) } == true
    }

    private fun hasRepositoryContract(javaClass: JavaClass): Boolean {
        val visited = mutableSetOf<String>()

        fun inspect(type: JavaClass): Boolean {
            if (!visited.add(type.name)) return false

            if (isRepositoryTypeName(type.packageName, type.name.substringAfterLast('.'))) return true

            if (type.rawInterfaces.any { iface ->
                    isRepositoryTypeName(iface.packageName, iface.name.substringAfterLast('.')) || inspect(iface)
                }) {
                return true
            }

            val superClass = type.rawSuperclass.orElse(null)
            if (superClass != null) {
                if (isRepositoryTypeName(superClass.packageName, superClass.name.substringAfterLast('.'))) return true
                if (inspect(superClass)) return true
            }

            return false
        }

        return inspect(javaClass)
    }

    private fun isRepositoryTypeName(packageName: String, simpleName: String): Boolean {
        val pkg = packageName.lowercase()
        val simple = simpleName.lowercase()

        return pkg.endsWith(".repository") ||
                pkg.contains(".repository.") ||
                simple == "repository" ||
                simple.endsWith("repository") ||
                simple.endsWith("crudrepository") ||
                simple.endsWith("jparepository") ||
                simple.endsWith("pagingandsortingrepository") ||
                simple.endsWith("mongorepository") ||
                simple.endsWith("reactiverepository")
    }

    private fun isService(last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "service" } ||
                last in setOf("service", "business", "usecase", "interactor") ||
                simple.endsWith("service") ||
                simple.endsWith("usecase") ||
                simple.endsWith("interactor")
    }

    private fun isEntity(last: String, simple: String, annotations: List<String>): Boolean {
        return annotations.any { it == "entity" || it == "table" } ||
                last in setOf("entity", "model", "domain") ||
                simple.endsWith("entity")
    }

    private fun isDto(last: String, simple: String): Boolean {
        return last in setOf("dto", "vo", "request", "response", "command", "query") ||
                simple.endsWith("dto") ||
                simple.endsWith("request") ||
                simple.endsWith("response") ||
                simple.endsWith("command") ||
                simple.endsWith("query")
    }

    private fun isPort(last: String, simple: String): Boolean {
        return last in setOf("port", "ports", "contract", "gateway", "spi") ||
                simple.endsWith("port")
    }

    private fun isAdapter(last: String, simple: String): Boolean {
        return last in setOf("adapter", "adapters") ||
                simple.endsWith("adapter")
    }

    private fun isApi(last: String, simple: String): Boolean {
        return last in setOf("api", "public", "contract", "exposed") ||
                simple.endsWith("api")
    }

    private fun isImpl(last: String, simple: String): Boolean {
        return last in setOf("impl", "implementation") || simple.endsWith("impl")
    }

    private fun isFeature(last: String, simple: String): Boolean {
        return last in setOf("feature", "module", "modules") || simple.contains("feature")
    }

    private fun isCommon(last: String, simple: String): Boolean {
        return last in setOf("common", "shared", "base", "kernel", "util") || simple.contains("common")
    }

    private fun isApplication(last: String, simple: String): Boolean {
        return last in setOf("application", "usecase", "interactor") ||
                simple.endsWith("usecase") ||
                simple.endsWith("interactor")
    }

    private fun isInfrastructure(last: String): Boolean {
        return last in setOf("infrastructure", "persistence", "adapter", "client", "integration", "external")
    }

    private fun isDomain(last: String): Boolean {
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