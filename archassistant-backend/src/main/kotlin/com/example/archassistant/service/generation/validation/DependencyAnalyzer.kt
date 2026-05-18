package com.example.archassistant.service.generation.validation

import com.example.archassistant.model.core.Violation
import com.example.archassistant.model.rules.ArchitecturalRule
import com.example.archassistant.model.rules.RuleType
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.nio.file.Path

object DependencyAnalyzer {

    fun calculateDependencyCorrect(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): Double {
        val accepted = setOf(
            RuleType.DEPENDENCY,
            RuleType.LAYER_ISOLATION,
            RuleType.CYCLE_CHECK,
            RuleType.INHERITANCE_CHECK,
            RuleType.INTERFACE_CHECK
        )
        return RuleViolationAnalyzer.evaluate(importedClasses, rules, accepted).score
    }

    fun calculateDependencyCorrect(classesDir: Path, rules: List<ArchitecturalRule>): Double {
        val imported = ClassFileImporter().importPath(classesDir)
        return calculateDependencyCorrect(imported, rules)
    }

    fun analyzeWithViolations(importedClasses: JavaClasses, rules: List<ArchitecturalRule>): List<Violation> {
        val accepted = setOf(
            RuleType.DEPENDENCY,
            RuleType.LAYER_ISOLATION,
            RuleType.CYCLE_CHECK,
            RuleType.INHERITANCE_CHECK,
            RuleType.INTERFACE_CHECK
        )
        return RuleViolationAnalyzer.combineViolations(importedClasses, rules, accepted)
    }

    fun analyzeWithViolations(classesDir: Path, rules: List<ArchitecturalRule>): List<Violation> {
        val imported = ClassFileImporter().importPath(classesDir)
        return analyzeWithViolations(imported, rules)
    }
}