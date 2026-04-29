package com.example.archassistant.service

import com.example.archassistant.model.*
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class ProjectStructureScanner {

    private val logger = LoggerFactory.getLogger(ProjectStructureScanner::class.java)

    fun scanProject(projectPath: String, projectId: String): ProjectStructure {
        logger.info("Scanning project: {} at path: {}", projectId, projectPath)

        val classesDir = findClassesDirectory(projectPath)
            ?: throw IllegalArgumentException("Cannot find compiled classes in $projectPath")

        val classes: JavaClasses = ClassFileImporter().importPath(classesDir)

        val packages = classes
            .map { it.packageName }
            .distinct()

        val annotations = extractAnnotations(classes)
        val dependencies = extractDependencies(classes)
        val architecturePattern = ArchitecturePattern.fromLayers(packages, annotations)
        val namingConventions = extractNamingConventions(classes)

        logger.info(
            "Project scanned: {} packages, {} annotations, pattern={}",
            packages.size, annotations.size, architecturePattern
        )

        return ProjectStructure(
            projectId = projectId,
            architecturePattern = architecturePattern,
            packages = packages,
            annotations = annotations,
            dependencies = dependencies,
            namingConventions = namingConventions
        )
    }

    fun scanProjectFromConfig(projectId: String, ruleRepository: YamlRuleRepository): ProjectStructure? {
        val config = ruleRepository.load(projectId)
        val projectPath = config?.projectPath
            ?: return null

        return if (Files.exists(Paths.get(projectPath))) {
            scanProject(projectPath, projectId)
        } else {
            logger.warn("Project path not found: {}", projectPath)
            null
        }
    }

    private fun findClassesDirectory(projectPath: String): Path? {
        val possiblePaths = listOf(
            Paths.get(projectPath, "build", "classes", "kotlin", "main"),
            Paths.get(projectPath, "build", "classes", "java", "main"),
            Paths.get(projectPath, "target", "classes"),
            Paths.get(projectPath, "out", "production", projectPath.substringAfterLast('/'))
        )

        return possiblePaths.firstOrNull { Files.exists(it) && Files.isDirectory(it) }
    }

    private fun extractAnnotations(classes: JavaClasses): Map<String, Int> {
        return classes
            .flatMap { javaClass ->
                javaClass.annotations.map { annotation -> annotation.type.name }
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun extractDependencies(classes: JavaClasses): List<Dependency> {
        return classes
            .flatMap { sourceClass ->
                sourceClass.directDependenciesFromSelf.map { dep ->
                    Dependency(
                        from = sourceClass.name,
                        to = dep.targetClass.name,
                        type = when {
                            dep.targetClass.isInterface -> DependencyType.INHERITANCE
                            else -> DependencyType.IMPORT
                        }
                    )
                }
            }
    }

    private fun extractNamingConventions(classes: JavaClasses): NamingConventions {
        val serviceClasses = classes.filter { javaClass ->
            javaClass.packageName.contains("service") ||
                    javaClass.isAnnotatedWith(org.springframework.stereotype.Service::class.java)
        }

        val repositoryClasses = classes.filter { javaClass ->
            javaClass.packageName.contains("repository") ||
                    javaClass.isAnnotatedWith(org.springframework.stereotype.Repository::class.java)
        }

        val controllerClasses = classes.filter { javaClass ->
            javaClass.packageName.contains("controller") ||
                    javaClass.isAnnotatedWith(org.springframework.stereotype.Controller::class.java)
        }

        val serviceSuffix = findMostCommonSuffix(serviceClasses, "Service")
        val repositorySuffix = findMostCommonSuffix(repositoryClasses, "Repository")
        val controllerSuffix = findMostCommonSuffix(controllerClasses, "Controller")

        return NamingConventions(
            serviceSuffix = serviceSuffix,
            repositorySuffix = repositorySuffix,
            controllerSuffix = controllerSuffix
        )
    }

    private fun findMostCommonSuffix(classes: List<JavaClass>, default: String): String {
        if (classes.isEmpty()) return default

        val suffixes = classes
            .map { it.simpleName }
            .mapNotNull { name ->
                when {
                    name.endsWith("Service") -> "Service"
                    name.endsWith("ServiceImpl") -> "ServiceImpl"
                    name.endsWith("Repository") -> "Repository"
                    name.endsWith("Controller") -> "Controller"
                    name.endsWith("ControllerImpl") -> "ControllerImpl"
                    else -> null
                }
            }
            .groupingBy { it }
            .eachCount()

        return suffixes.maxByOrNull { it.value }?.key ?: default
    }
}