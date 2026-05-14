package com.example.archassistant.util

import com.example.archassistant.model.ClassType
import com.example.archassistant.model.LayerType
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.RuleType
import com.example.archassistant.model.rules.ConstraintType
import com.example.archassistant.model.rules.SelectorMode
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.*
import com.tngtech.archunit.lang.*
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition

object ArchUnitRuleBuilder {

    fun build(rule: ArchitecturalRule): ArchRule? {
        return when (rule.type) {
            RuleType.DEPENDENCY -> buildDependencyRule(rule)
            RuleType.LAYER_ISOLATION -> buildDependencyRule(rule)
            RuleType.NAMING_CONVENTION -> buildNamingRule(rule)
            RuleType.ANNOTATION_CHECK -> buildAnnotationRule(rule)
            RuleType.CYCLE_CHECK -> buildCycleRule(rule)
            RuleType.INHERITANCE_CHECK -> buildInheritanceRule(rule)
            RuleType.INTERFACE_CHECK -> buildInterfaceRule(rule)
            RuleType.MODIFIER_CHECK -> buildModifierRule(rule)
            RuleType.METHOD_SIGNATURE_CHECK -> buildMethodSignatureRule(rule)
            RuleType.FIELD_CHECK -> buildFieldRule(rule)
            RuleType.EXCEPTION_CHECK -> buildExceptionRule(rule)
            RuleType.CUSTOM -> null
        }
    }

    private fun buildDependencyRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val toPredicate = predicateFor(rule, isFrom = false)

        val base = when (rule.constraint) {
            ConstraintType.NO_DEPENDENCY ->
                ArchRuleDefinition.noClasses().that(fromPredicate).should().dependOnClassesThat(toPredicate)

            ConstraintType.MUST_DEPEND ->
                ArchRuleDefinition.classes().that(fromPredicate).should().dependOnClassesThat(toPredicate)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for DEPENDENCY rules")
        }

        return base.because(rule.description ?: rule.name)
    }

    private fun buildNamingRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val pattern = rule.pattern ?: ""

        return when (rule.constraint) {
            ConstraintType.NAMING_SUFFIX ->
                ArchRuleDefinition.classes().that(fromPredicate).should().haveSimpleNameEndingWith(pattern)
                    .because(rule.description ?: rule.name)

            ConstraintType.NAMING_PREFIX ->
                ArchRuleDefinition.classes().that(fromPredicate).should().haveSimpleNameStartingWith(pattern)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for NAMING_CONVENTION rules")
        }
    }

    private fun buildAnnotationRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val annotation = rule.annotation ?: ""

        return when (rule.constraint) {
            ConstraintType.HAS_ANNOTATION ->
                ArchRuleDefinition.classes().that(fromPredicate).should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            ConstraintType.NO_ANNOTATION ->
                ArchRuleDefinition.noClasses().that(fromPredicate).should().beAnnotatedWith(annotation)
                    .because(rule.description ?: rule.name)

            else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for ANNOTATION_CHECK rules")
        }
    }

    private fun buildCycleRule(rule: ArchitecturalRule): ArchRule {
        val slicePattern = rule.slicePattern ?: rule.fromPackage
        if (slicePattern.isBlank()) {
            throw IllegalArgumentException("slicePattern/fromPackage is required for CYCLE_CHECK rules")
        }

        val cyclesRule = SlicesRuleDefinition.slices()
            .matching(slicePattern)
            .should()
            .beFreeOfCycles()

        return cyclesRule.because(rule.description ?: rule.name)
    }

    private fun buildInheritanceRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val targetPatterns = targetPatterns(rule)

        val condition = object : ArchCondition<JavaClass>("inherit according to ${rule.constraint}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val matches = when (rule.constraint) {
                    ConstraintType.SHOULD_EXTEND -> hasMatchingSuperclass(item, targetPatterns)
                    ConstraintType.SHOULD_NOT_EXTEND -> !hasMatchingSuperclass(item, targetPatterns)
                    else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for INHERITANCE_CHECK rules")
                }

                if (!matches) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} does not satisfy ${rule.constraint} for target ${targetPatterns.joinToString(", ")}"
                        )
                    )
                }
            }
        }

        return ArchRuleDefinition.classes().that(fromPredicate).should(condition).because(rule.description ?: rule.name)
    }

    private fun buildInterfaceRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)
        val targetPatterns = targetPatterns(rule)

        val condition = object : ArchCondition<JavaClass>("implement according to ${rule.constraint}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val matches = when (rule.constraint) {
                    ConstraintType.SHOULD_IMPLEMENT -> implementsMatchingInterface(item, targetPatterns)
                    ConstraintType.SHOULD_NOT_IMPLEMENT -> !implementsMatchingInterface(item, targetPatterns)
                    else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for INTERFACE_CHECK rules")
                }

                if (!matches) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} does not satisfy ${rule.constraint} for target ${targetPatterns.joinToString(", ")}"
                        )
                    )
                }
            }
        }

        return ArchRuleDefinition.classes().that(fromPredicate).should(condition).because(rule.description ?: rule.name)
    }

    private fun buildModifierRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)

        val condition = object : ArchCondition<JavaClass>("match modifier rule ${rule.constraint}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val mods = item.modifiers
                val matches = when (rule.constraint) {
                    ConstraintType.SHOULD_BE_PUBLIC -> JavaModifier.PUBLIC in mods
                    ConstraintType.SHOULD_NOT_BE_PUBLIC -> JavaModifier.PUBLIC !in mods
                    ConstraintType.SHOULD_BE_FINAL -> JavaModifier.FINAL in mods
                    ConstraintType.SHOULD_NOT_BE_FINAL -> JavaModifier.FINAL !in mods
                    ConstraintType.SHOULD_BE_ABSTRACT -> JavaModifier.ABSTRACT in mods
                    ConstraintType.SHOULD_NOT_BE_ABSTRACT -> JavaModifier.ABSTRACT !in mods
                    else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for MODIFIER_CHECK rules")
                }

                if (!matches) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} does not satisfy ${rule.constraint}"
                        )
                    )
                }
            }
        }

        return ArchRuleDefinition.classes().that(fromPredicate).should(condition).because(rule.description ?: rule.name)
    }

    private fun buildMethodSignatureRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)

        val condition = object : ArchCondition<JavaClass>("match method signature rule ${rule.constraint}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val methods = item.methods
                val matches = when (rule.constraint) {
                    ConstraintType.RETURN_TYPE -> methods.any { matchesTypeName(it.rawReturnType, rule.fromReturnType) || matchesTypeName(it.rawReturnType, rule.toReturnType) }
                    ConstraintType.PARAMETER_COUNT -> {
                        val expected = rule.fromParameterTypes?.size ?: rule.toParameterTypes?.size
                        expected != null && methods.any { it.rawParameterTypes.size == expected }
                    }
                    ConstraintType.PARAMETER_TYPES -> {
                        val expected = rule.fromParameterTypes ?: rule.toParameterTypes
                        expected != null && methods.any { method ->
                            method.rawParameterTypes.map { it.name }.map { normalizeTypeName(it) } == expected.map { normalizeTypeName(it) }
                        }
                    }
                    ConstraintType.METHOD_VISIBILITY -> {
                        val expected = rule.fromModifiers ?: rule.toModifiers
                        expected != null && methods.any { method ->
                            val methodMods = method.modifiers.map { it.name.lowercase() }.toSet()
                            expected.any { it.lowercase() in methodMods }
                        }
                    }
                    ConstraintType.METHOD_NAME_PATTERN -> {
                        val expected = rule.fromMethodNamePattern ?: rule.toMethodNamePattern ?: rule.pattern
                        expected != null && methods.any { method -> method.name.matches(globToRegex(expected)) }
                    }
                    else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for METHOD_SIGNATURE_CHECK rules")
                }

                if (!matches) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} does not satisfy method signature constraint ${rule.constraint}"
                        )
                    )
                }
            }
        }

        return ArchRuleDefinition.classes().that(fromPredicate).should(condition).because(rule.description ?: rule.name)
    }

    private fun buildFieldRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)

        val condition = object : ArchCondition<JavaClass>("match field rule ${rule.constraint}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val fields = item.fields
                val matches = when (rule.constraint) {
                    ConstraintType.FIELD_TYPE -> {
                        val expected = rule.fromFieldType ?: rule.toFieldType
                        expected != null && fields.any { matchesTypeName(it.rawType, expected) }
                    }
                    ConstraintType.FIELD_VISIBILITY -> {
                        val expected = rule.fromModifiers ?: rule.toModifiers
                        expected != null && fields.any { field ->
                            val mods = field.modifiers.map { it.name.lowercase() }.toSet()
                            expected.any { it.lowercase() in mods }
                        }
                    }
                    ConstraintType.FIELD_ANNOTATION -> {
                        val expected = rule.annotation?.removePrefix("@")
                        expected != null && fields.any { field ->
                            field.annotations.any { ann ->
                                ann.type.name.substringAfterLast('.').equals(expected, ignoreCase = true)
                            }
                        }
                    }
                    ConstraintType.FIELD_NAME_PATTERN -> {
                        val expected = rule.fromFieldNamePattern ?: rule.toFieldNamePattern ?: rule.pattern
                        expected != null && fields.any { field -> field.name.matches(globToRegex(expected)) }
                    }
                    else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for FIELD_CHECK rules")
                }

                if (!matches) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} does not satisfy field constraint ${rule.constraint}"
                        )
                    )
                }
            }
        }

        return ArchRuleDefinition.classes().that(fromPredicate).should(condition).because(rule.description ?: rule.name)
    }

    private fun buildExceptionRule(rule: ArchitecturalRule): ArchRule {
        val fromPredicate = predicateFor(rule, isFrom = true)

        val condition = object : ArchCondition<JavaClass>("match exception rule ${rule.constraint}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val allowed = rule.fromThrowsTypes ?: rule.toThrowsTypes ?: emptyList()
                val methods = item.methods

                val matches = when (rule.constraint) {
                    ConstraintType.SHOULD_ONLY_THROW -> {
                        if (allowed.isEmpty()) false else methods.all { method ->
                            val thrownTypes = method.throwsClause.types.map { it.name }
                            thrownTypes.all { thrown -> allowed.any { normalizeTypeName(it) == normalizeTypeName(thrown) } }
                        }
                    }

                    ConstraintType.SHOULD_NOT_THROW -> {
                        if (allowed.isEmpty()) false else methods.none { method ->
                            val thrownTypes = method.throwsClause.types.map { it.name }
                            thrownTypes.any { thrown -> allowed.any { normalizeTypeName(it) == normalizeTypeName(thrown) } }
                        }
                    }

                    else -> throw UnsupportedOperationException("Constraint ${rule.constraint} not supported for EXCEPTION_CHECK rules")
                }

                if (!matches) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} does not satisfy exception constraint ${rule.constraint}"
                        )
                    )
                }
            }
        }

        return ArchRuleDefinition.classes().that(fromPredicate).should(condition).because(rule.description ?: rule.name)
    }

    private fun predicateFor(rule: ArchitecturalRule, isFrom: Boolean): DescribedPredicate<JavaClass> {
        val selectorMode = if (isFrom) rule.fromSelectorMode else rule.toSelectorMode
        val classType = if (isFrom) rule.fromClassType else rule.toClassType
        val layerType = if (isFrom) rule.fromLayerType else rule.toLayerType
        val annotation = rule.annotation
        val namePattern = if (isFrom) rule.fromNamePattern else rule.toNamePattern
        val packages: List<String> = if (isFrom) {
            listOf(rule.fromPackage)
        } else {
            rule.toPackages?.ifEmpty { null } ?: listOfNotNull(rule.toPackage)
        }

        val predicates = mutableListOf<DescribedPredicate<JavaClass>>()

        if (packages.isNotEmpty()) {
            predicates.add(packagePredicate(packages))
        }

        when (selectorMode) {
            SelectorMode.CLASS_TYPE -> {
                if (classType != null) predicates.add(classTypePredicate(classType))
            }

            SelectorMode.LAYER -> {
                if (layerType != null) predicates.add(layerPredicate(layerType))
            }

            SelectorMode.ANNOTATION -> {
                if (!annotation.isNullOrBlank()) predicates.add(annotationPredicate(annotation))
            }

            SelectorMode.MEMBER -> {
                if (!namePattern.isNullOrBlank()) {
                    predicates.add(namePredicate(namePattern))
                }
            }

            SelectorMode.PACKAGE -> {
                if (!namePattern.isNullOrBlank()) {
                    predicates.add(namePredicate(namePattern))
                }
            }
        }

        return if (predicates.isEmpty()) {
            DescribedPredicate.alwaysTrue()
        } else {
            predicates.reduce { acc, predicate -> acc.and(predicate) }
        }
    }

    private fun packagePredicate(patterns: List<String>): DescribedPredicate<JavaClass> {
        val predicates = patterns
            .filter { it.isNotBlank() }
            .map { packagePredicateForPattern(it) }

        return when (predicates.size) {
            0 -> neverPredicate("no package patterns")
            1 -> predicates.single()
            else -> predicates.reduce { acc, predicate -> acc.or(predicate) }
        }
    }

    private fun packagePredicateForPattern(pattern: String): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>("reside in package pattern '$pattern'") {
            override fun test(item: JavaClass): Boolean {
                return PackagePatternBuilder.matches(pattern, item.packageName)
            }
        }
    }

    private fun targetPatterns(rule: ArchitecturalRule): List<String> {
        return buildList {
            rule.toPackage?.let { add(it) }
            rule.toPackages?.let { addAll(it) }
            rule.toNamePattern?.let { add(it) }
            rule.toClassType?.let { add(it.name) }
            rule.toLayerType?.let { add(it.name) }
        }.filter { it.isNotBlank() }
    }

    private fun hasMatchingSuperclass(item: JavaClass, targetPatterns: List<String>): Boolean {
        var current = item.rawSuperclass.orElse(null)
        while (current != null) {
            if (matchesAnyPattern(current, targetPatterns)) return true
            current = current.rawSuperclass.orElse(null)
        }
        return false
    }

    private fun implementsMatchingInterface(item: JavaClass, targetPatterns: List<String>): Boolean {
        val visited = mutableSetOf<String>()

        fun visit(clazz: JavaClass): Boolean {
            if (!visited.add(clazz.name)) return false
            if (clazz.rawInterfaces.any { matchesAnyPattern(it, targetPatterns) }) return true
            if (clazz.rawInterfaces.any { visit(it) }) return true
            val superClass = clazz.rawSuperclass.orElse(null)
            return superClass?.let { visit(it) } == true
        }

        return visit(item)
    }

    private fun matchesAnyPattern(javaClass: JavaClass, targetPatterns: List<String>): Boolean {
        if (targetPatterns.isEmpty()) return false
        val fullName = javaClass.name
        val simpleName = javaClass.simpleName
        val pkg = javaClass.packageName
        return targetPatterns.any { pattern ->
            when {
                pattern.contains('.') -> matchesGlob(pattern, fullName) || matchesGlob(pattern, pkg)
                else -> simpleName.equals(pattern, ignoreCase = true) ||
                        simpleName.matches(globToRegex(pattern)) ||
                        fullName.endsWith(".$pattern") ||
                        pkg.endsWith(".$pattern")
            }
        }
    }

    private fun annotationPredicate(annotation: String?): DescribedPredicate<JavaClass> {
        val normalized = annotation?.removePrefix("@")?.lowercase().orEmpty()
        return object : DescribedPredicate<JavaClass>("be annotated with '$annotation'") {
            override fun test(item: JavaClass): Boolean {
                if (normalized.isBlank()) return false
                return item.annotations.any { ann ->
                    ann.type.name.substringAfterLast('.').lowercase() == normalized
                }
            }
        }
    }

    private fun classTypePredicate(type: ClassType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return neverPredicate("match no class type")
        return object : DescribedPredicate<JavaClass>("match class type '$resolved'") {
            override fun test(item: JavaClass): Boolean = ProjectLayerClassifier.matchesClassType(item, resolved)
        }
    }

    private fun layerPredicate(type: LayerType?): DescribedPredicate<JavaClass> {
        val resolved = type ?: return neverPredicate("match no layer")
        return object : DescribedPredicate<JavaClass>("match layer '$resolved'") {
            override fun test(item: JavaClass): Boolean = ProjectLayerClassifier.matchesLayer(item, resolved)
        }
    }

    private fun namePredicate(pattern: String): DescribedPredicate<JavaClass> {
        val regex = globToRegex(pattern)
        return object : DescribedPredicate<JavaClass>("match name pattern '$pattern'") {
            override fun test(item: JavaClass): Boolean {
                val fullName = item.name
                val simpleName = item.simpleName
                return simpleName.matches(regex) || fullName.matches(regex) || simpleName.contains(pattern, ignoreCase = true)
            }
        }
    }

    private fun matchesTypeName(javaClass: JavaClass, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return false
        val normalizedExpected = normalizeTypeName(expected)
        return normalizeTypeName(javaClass.name).endsWith(normalizedExpected) ||
                normalizeTypeName(javaClass.simpleName) == normalizedExpected ||
                normalizeTypeName(javaClass.fullName).endsWith(normalizedExpected)
    }

    private fun normalizeTypeName(value: String): String =
        value.removePrefix("@").substringAfterLast('.').lowercase()

    private fun matchesGlob(pattern: String, value: String): Boolean =
        value.matches(globToRegex(pattern))

    private fun globToRegex(pattern: String): Regex {
        val normalized = pattern
            .trim()
            .replace(".", "\\.")
            .replace("**", "§§DOUBLE_WILDCARD§§")
            .replace("*", "[^.]*")
            .replace("§§DOUBLE_WILDCARD§§", ".*")
        return Regex("^$normalized$")
    }

    private fun neverPredicate(description: String): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>(description) {
            override fun test(item: JavaClass): Boolean = false
        }
    }
}