package com.example.archassistant.util

import com.example.archassistant.model.ArchitecturalRule
import com.example.archassistant.model.RuleType
import com.example.archassistant.model.Violation
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.nio.file.Path

object PatternMatcher {

    fun calculatePatternMatch(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): Double {
        val accepted = setOf(
            RuleType.NAMING_CONVENTION,
            RuleType.ANNOTATION_CHECK,
            RuleType.MODIFIER_CHECK,
            RuleType.METHOD_SIGNATURE_CHECK,
            RuleType.FIELD_CHECK,
            RuleType.EXCEPTION_CHECK
        )
        return RuleViolationAnalyzer.evaluate(importedClasses, rules, accepted).score
    }

    fun calculatePatternMatch(classesDir: Path, rules: List<ArchitecturalRule>): Double {
        val imported = ClassFileImporter().importPath(classesDir)
        return calculatePatternMatch(imported, rules)
    }

    fun checkWithViolations(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): List<Violation> {
        val accepted = setOf(
            RuleType.NAMING_CONVENTION,
            RuleType.ANNOTATION_CHECK,
            RuleType.MODIFIER_CHECK,
            RuleType.METHOD_SIGNATURE_CHECK,
            RuleType.FIELD_CHECK,
            RuleType.EXCEPTION_CHECK
        )
        return RuleViolationAnalyzer.combineViolations(importedClasses, rules, accepted)
    }

    fun checkWithViolations(classesDir: Path, rules: List<ArchitecturalRule>): List<Violation> {
        val imported = ClassFileImporter().importPath(classesDir)
        return checkWithViolations(imported, rules)
    }
}