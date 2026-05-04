package com.example.archassistant.model

object RuleValidationCatalog {

    private val allowedConstraints: Map<RuleType, Set<ConstraintType>> = mapOf(
        RuleType.DEPENDENCY to setOf(ConstraintType.NO_DEPENDENCY, ConstraintType.MUST_DEPEND, ConstraintType.CUSTOM),
        RuleType.LAYER_ISOLATION to setOf(ConstraintType.NO_DEPENDENCY, ConstraintType.MUST_DEPEND, ConstraintType.CUSTOM),
        RuleType.NAMING_CONVENTION to setOf(ConstraintType.NAMING_SUFFIX, ConstraintType.NAMING_PREFIX, ConstraintType.CUSTOM),
        RuleType.ANNOTATION_CHECK to setOf(ConstraintType.HAS_ANNOTATION, ConstraintType.NO_ANNOTATION, ConstraintType.CUSTOM),
        RuleType.CYCLE_CHECK to setOf(ConstraintType.NO_CYCLE, ConstraintType.MAX_CYCLE_LENGTH, ConstraintType.CUSTOM),
        RuleType.INHERITANCE_CHECK to setOf(ConstraintType.SHOULD_EXTEND, ConstraintType.SHOULD_NOT_EXTEND, ConstraintType.CUSTOM),
        RuleType.INTERFACE_CHECK to setOf(ConstraintType.SHOULD_IMPLEMENT, ConstraintType.SHOULD_NOT_IMPLEMENT, ConstraintType.CUSTOM),
        RuleType.MODIFIER_CHECK to setOf(
            ConstraintType.SHOULD_BE_PUBLIC,
            ConstraintType.SHOULD_NOT_BE_PUBLIC,
            ConstraintType.SHOULD_BE_FINAL,
            ConstraintType.SHOULD_NOT_BE_FINAL,
            ConstraintType.SHOULD_BE_ABSTRACT,
            ConstraintType.SHOULD_NOT_BE_ABSTRACT,
            ConstraintType.CUSTOM
        ),
        RuleType.METHOD_SIGNATURE_CHECK to setOf(
            ConstraintType.RETURN_TYPE,
            ConstraintType.PARAMETER_COUNT,
            ConstraintType.PARAMETER_TYPES,
            ConstraintType.METHOD_VISIBILITY,
            ConstraintType.METHOD_NAME_PATTERN,
            ConstraintType.CUSTOM
        ),
        RuleType.FIELD_CHECK to setOf(
            ConstraintType.FIELD_TYPE,
            ConstraintType.FIELD_VISIBILITY,
            ConstraintType.FIELD_ANNOTATION,
            ConstraintType.FIELD_NAME_PATTERN,
            ConstraintType.CUSTOM
        ),
        RuleType.EXCEPTION_CHECK to setOf(
            ConstraintType.SHOULD_ONLY_THROW,
            ConstraintType.SHOULD_NOT_THROW,
            ConstraintType.CUSTOM
        ),
        RuleType.CUSTOM to setOf(ConstraintType.CUSTOM)
    )

    fun allowedConstraints(type: RuleType): Set<ConstraintType> = allowedConstraints[type].orEmpty()

    fun isMemberRule(type: RuleType): Boolean = type in setOf(
        RuleType.MODIFIER_CHECK,
        RuleType.METHOD_SIGNATURE_CHECK,
        RuleType.FIELD_CHECK,
        RuleType.EXCEPTION_CHECK
    )

    fun isStructuralRule(type: RuleType): Boolean = type in setOf(
        RuleType.CYCLE_CHECK,
        RuleType.INHERITANCE_CHECK,
        RuleType.INTERFACE_CHECK,
        RuleType.LAYER_ISOLATION,
        RuleType.DEPENDENCY
    )

    fun isPatternRule(type: RuleType): Boolean = type in setOf(
        RuleType.NAMING_CONVENTION,
        RuleType.ANNOTATION_CHECK
    )

    fun expectedRequiredFields(rule: ArchitecturalRule): List<String> {
        val fields = mutableListOf<String>()

        when (rule.type) {
            RuleType.DEPENDENCY, RuleType.LAYER_ISOLATION -> {
                fields += "from_package"
                if (rule.fromSelectorMode == SelectorMode.PACKAGE && rule.fromPackage.isBlank()) fields += "from_package"
                if (rule.constraint in setOf(ConstraintType.NO_DEPENDENCY, ConstraintType.MUST_DEPEND)) {
                    if (rule.toPackage.isNullOrBlank() && rule.toPackages.isNullOrEmpty()) fields += "to_package|to_packages"
                }
            }

            RuleType.NAMING_CONVENTION -> {
                if (rule.pattern.isNullOrBlank()) fields += "pattern"
            }

            RuleType.ANNOTATION_CHECK -> {
                if (rule.annotation.isNullOrBlank()) fields += "annotation"
            }

            RuleType.CYCLE_CHECK -> {
                if (rule.slicePattern.isNullOrBlank()) fields += "slice_pattern"
            }

            RuleType.INHERITANCE_CHECK, RuleType.INTERFACE_CHECK -> {
                if (rule.toPackage.isNullOrBlank() && rule.toPackages.isNullOrEmpty()) fields += "to_package|to_packages"
            }

            RuleType.MODIFIER_CHECK -> {
                if (rule.fromPackage.isBlank()) fields += "from_package"
            }

            RuleType.METHOD_SIGNATURE_CHECK -> {
                if (rule.fromPackage.isBlank()) fields += "from_package"
            }

            RuleType.FIELD_CHECK -> {
                if (rule.fromPackage.isBlank()) fields += "from_package"
            }

            RuleType.EXCEPTION_CHECK -> {
                if (rule.fromPackage.isBlank()) fields += "from_package"
            }

            RuleType.CUSTOM -> Unit
        }

        return fields.distinct()
    }

    fun selectorModesAllowed(type: RuleType): Set<SelectorMode> = when (type) {
        RuleType.DEPENDENCY, RuleType.LAYER_ISOLATION, RuleType.CYCLE_CHECK -> setOf(
            SelectorMode.PACKAGE, SelectorMode.CLASS_TYPE, SelectorMode.LAYER, SelectorMode.ANNOTATION
        )

        RuleType.NAMING_CONVENTION, RuleType.ANNOTATION_CHECK -> setOf(
            SelectorMode.PACKAGE, SelectorMode.CLASS_TYPE, SelectorMode.LAYER, SelectorMode.ANNOTATION
        )

        RuleType.INHERITANCE_CHECK, RuleType.INTERFACE_CHECK -> setOf(
            SelectorMode.PACKAGE, SelectorMode.CLASS_TYPE, SelectorMode.LAYER, SelectorMode.ANNOTATION
        )

        RuleType.MODIFIER_CHECK, RuleType.METHOD_SIGNATURE_CHECK, RuleType.FIELD_CHECK, RuleType.EXCEPTION_CHECK -> setOf(
            SelectorMode.PACKAGE, SelectorMode.CLASS_TYPE, SelectorMode.LAYER, SelectorMode.ANNOTATION, SelectorMode.MEMBER
        )

        RuleType.CUSTOM -> SelectorMode.entries.toSet()
    }
}