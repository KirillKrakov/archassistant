package com.example.archassistant.util

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.ConstraintType
import com.example.archassistant.model.RuleType
import com.example.archassistant.model.Severity

object PromptFormatter {

    fun formatSystemPrompt(
        rules: List<ArchitecturalRule>,
        languageHint: String? = null
    ): String {
        val language = languageHint?.takeIf { it.isNotBlank() } ?: "Java/Kotlin"

        val rulesSection = if (rules.isNotEmpty()) {
            """
            |PROJECT ARCHITECTURAL RULES (mandatory to follow):
            |${rules.joinToString("\n") { formatRule(it) }}
            |
            |If the generated code violates these rules, it will be rejected.
            """.trimMargin()
        } else {
            ""
        }

        return """
            You are an experienced $language developer.
            Your task is to generate clean, efficient code while following the project's architectural standards.

            $rulesSection

            INSTRUCTIONS:
            1. Return ONLY pure code, without explanations, comments, or markdown.
            2. Use best practices for $language projects.
            3. If a rule conflicts with the request, the architectural rule has priority.
            4. If the request is incomplete, make reasonable assumptions only within the existing project context.
            5. If the solution requires multiple classes, return them as separate complete source files.
            6. Each source file must start with its own package declaration.
            7. Never combine multiple package declarations in a single file.
            8. If the user prompt contains project context, use only existing packages, classes, methods, and accessible public API from it.
            9. Do not invent project-specific package roots that do not exist in the project context.
            10. If you implement an existing interface or repository contract, include all abstract methods with exact signatures from the context.
            11. Never generate a partial interface implementation: if an interface is selected, it must compile without missing abstract methods.
            12. For simple immutable DTO/value objects in Java 16+, prefer record when it does not conflict with the project context.
            13. If record is not suitable, use final fields + all-args constructor + getters.
            14. Standard JDK/Spring/Jakarta imports are allowed when needed.
            15. Do not invent project-specific imports outside knownPackages.
            16. If the context defines a class contract, do not add extra fields, constructor parameters, or methods beyond that contract.
            17. Treat nested types with canonical Java dotted syntax (Outer.Inner), not with '$'.
            18. Avoid referencing package-private classes from a different package; only use them inside the same package.
            19. Do not invent framework API members, annotation attributes, or inherited methods.
                Use only names explicitly listed in the context or guaranteed by the visible contract.
            20. For annotations, prefer minimal usage; if an attribute is not explicitly known, omit it rather than guessing.
        """.trimIndent()
    }

    private fun formatRule(rule: ArchitecturalRule): String {
        val prefix = when (rule.severity) {
            Severity.CRITICAL -> "[MANDATORY] "
            Severity.ERROR -> "[REQUIRED] "
            Severity.WARNING -> "[RECOMMENDED] "
            else -> ""
        }

        return when (rule.type) {
            RuleType.DEPENDENCY -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: "*"
                "$prefix${rule.name}: classes in `${rule.fromPackage}` must not depend on `$target`"
            }

            RuleType.LAYER_ISOLATION -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: "*"
                "$prefix${rule.name}: classes in `${rule.fromPackage}` must not depend on `$target`"
            }

            RuleType.NAMING_CONVENTION -> {
                "$prefix${rule.name}: classes in `${rule.fromPackage}` must ${
                    if (rule.constraint == ConstraintType.NAMING_SUFFIX) "end with" else "start with"
                } `${rule.pattern}`"
            }

            RuleType.ANNOTATION_CHECK -> {
                "$prefix${rule.name}: classes in `${rule.fromPackage}` must ${
                    if (rule.constraint == ConstraintType.HAS_ANNOTATION) "have" else "not have"
                } annotation `${rule.annotation}`"
            }

            RuleType.CYCLE_CHECK -> {
                val slice = rule.slicePattern ?: rule.fromPackage
                "$prefix${rule.name}: package slices `$slice` must be free of cyclic dependencies"
            }

            RuleType.INHERITANCE_CHECK -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: rule.toClassType?.name ?: "*"
                val action = if (rule.constraint == ConstraintType.SHOULD_NOT_EXTEND) {
                    "must not extend"
                } else {
                    "must extend"
                }
                "$prefix${rule.name}: classes in `${rule.fromPackage}` $action `$target`"
            }

            RuleType.INTERFACE_CHECK -> {
                val target = rule.toPackage ?: rule.toPackages?.joinToString(", ") ?: rule.toClassType?.name ?: "*"
                val action = if (rule.constraint == ConstraintType.SHOULD_NOT_IMPLEMENT) {
                    "must not implement"
                } else {
                    "must implement"
                }
                "$prefix${rule.name}: classes in `${rule.fromPackage}` $action `$target`"
            }

            RuleType.MODIFIER_CHECK -> {
                val modifier = when (rule.constraint) {
                    ConstraintType.SHOULD_BE_PUBLIC, ConstraintType.SHOULD_NOT_BE_PUBLIC -> "public"
                    ConstraintType.SHOULD_BE_FINAL, ConstraintType.SHOULD_NOT_BE_FINAL -> "final"
                    ConstraintType.SHOULD_BE_ABSTRACT, ConstraintType.SHOULD_NOT_BE_ABSTRACT -> "abstract"
                    else -> rule.constraint.name.lowercase()
                }
                val action = when (rule.constraint) {
                    ConstraintType.SHOULD_NOT_BE_PUBLIC,
                    ConstraintType.SHOULD_NOT_BE_FINAL,
                    ConstraintType.SHOULD_NOT_BE_ABSTRACT -> "must not be"
                    else -> "must be"
                }
                "$prefix${rule.name}: classes in `${rule.fromPackage}` $action `$modifier`"
            }

            RuleType.METHOD_SIGNATURE_CHECK -> {
                val parts = mutableListOf<String>()
                rule.fromMethodNamePattern?.takeIf { it.isNotBlank() }?.let { parts += "name matches pattern `$it`" }
                rule.fromReturnType?.takeIf { it.isNotBlank() }?.let { parts += "return `$it`" }
                rule.fromParameterTypes?.takeIf { it.isNotEmpty() }?.let { parts += "accept parameters `${it.joinToString(", ")}`" }
                rule.fromModifiers?.takeIf { it.isNotEmpty() }?.let { parts += "have modifiers `${it.joinToString(", ")}`" }

                val details = if (parts.isNotEmpty()) parts.joinToString("; ") else "have the required signature"
                "$prefix${rule.name}: methods in classes `${rule.fromPackage}` must satisfy `${rule.constraint}`; $details"
            }

            RuleType.FIELD_CHECK -> {
                val parts = mutableListOf<String>()
                rule.fromFieldNamePattern?.takeIf { it.isNotBlank() }?.let { parts += "name matches pattern `$it`" }
                rule.fromFieldType?.takeIf { it.isNotBlank() }?.let { parts += "type `$it`" }
                rule.annotation?.takeIf { it.isNotBlank() }?.let { parts += "annotation `$it`" }
                rule.fromModifiers?.takeIf { it.isNotEmpty() }?.let { parts += "modifiers `${it.joinToString(", ")}`" }

                val details = if (parts.isNotEmpty()) parts.joinToString("; ") else "satisfy the required property"
                "$prefix${rule.name}: fields in classes `${rule.fromPackage}` must satisfy `${rule.constraint}`; $details"
            }

            RuleType.EXCEPTION_CHECK -> {
                val target = rule.fromThrowsTypes?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: rule.toThrowsTypes?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: "*"

                val action = when (rule.constraint) {
                    ConstraintType.SHOULD_ONLY_THROW -> "may only throw"
                    ConstraintType.SHOULD_NOT_THROW -> "must not throw"
                    else -> "must satisfy the exception constraint"
                }

                "$prefix${rule.name}: methods in classes `${rule.fromPackage}` $action `$target`"
            }

            RuleType.CUSTOM -> "$prefix${rule.name}: ${rule.description ?: rule.id}"
        }
    }

    fun formatUserPrompt(
        originalRequest: String,
        previousErrors: List<String> = emptyList(),
        projectContext: String? = null,
        codeContext: String? = null
    ): String {
        val projectSection = projectContext?.takeIf { it.isNotBlank() }?.let {
            """
            |PROJECT CONTEXT:
            |```text
            |$it
            |```
            """.trimMargin()
        } ?: ""

        val codeSection = codeContext?.takeIf { it.isNotBlank() }?.let {
            """
            |REFERENCE CODE FROM PROJECT:
            |```text
            |$it
            |```
            """.trimMargin()
        } ?: ""

        val errorSection = if (previousErrors.isNotEmpty()) {
            """
            |⚠️ THE PREVIOUS VERSION VIOLATED THE FOLLOWING RULES:
            |${previousErrors.joinToString("\n") { "- $it" }}
            |
            |Fix these errors in the new version.
            """.trimMargin()
        } else {
            ""
        }

        return """
            $originalRequest

            $projectSection
            $codeSection
            $errorSection

            Generate the code now.
            If multiple classes are needed, output them as separate complete source files,
            each with its own package declaration, without mixing multiple packages in one file.
            Use only existing packages and signatures from the project context.
            Do not invent project-specific imports outside knownPackages. Standard JDK/Spring/Jakarta imports are allowed.
            Treat nested types as canonical Java dotted names (Outer.Inner) and do not reference package-private classes outside their own package.
            Preserve the original intent of the request and do not change the artifact type unless explicitly required.
            Use only exact method names and annotation elements shown in the context.
            Do not infer methods from superinterfaces and do not invent annotation attributes.
        """.trimIndent()
    }
}