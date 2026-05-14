package com.example.archassistant.model.context

data class ClassInfo(
    val fullName: String,
    val simpleName: String,
    val packageName: String,
    val kind: ClassKind = ClassKind.CLASS,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val annotations: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val modifiers: List<String> = emptyList(),
    val fields: List<FieldInfo> = emptyList(),
    val constructors: List<ConstructorInfo> = emptyList(),
    val publicMethods: List<String> = emptyList(),
    val origin: ClassOrigin = ClassOrigin.BASE
)

val ClassInfo.canonicalName: String
    get() = fullName.trim().replace('$', '.')

val ClassInfo.displaySimpleName: String
    get() = canonicalName.substringAfterLast('.')

val ClassInfo.isPublicType: Boolean
    get() = modifiers.any { it.equals("public", ignoreCase = true) }

val ClassInfo.isProtectedType: Boolean
    get() = modifiers.any { it.equals("protected", ignoreCase = true) }

val ClassInfo.isPrivateType: Boolean
    get() = modifiers.any { it.equals("private", ignoreCase = true) }

val ClassInfo.isNestedType: Boolean
    get() {
        val canonical = canonicalName
        val pkg = packageName.trim().trim('.')

        val relative = if (pkg.isNotBlank() && canonical.startsWith("$pkg.")) {
            canonical.removePrefix("$pkg.").trim('.')
        } else {
            ""
        }

        return when {
            relative.isNotBlank() -> relative.contains('.')
            simpleName.contains('$') -> true
            else -> false
        }
    }

val ClassInfo.visibilityLabel: String
    get() = when {
        isPublicType -> "public"
        isProtectedType -> "protected"
        isPrivateType -> "private"
        else -> "package-private"
    }

fun ClassInfo.canonicalTypeAliases(): Set<String> {
    val aliases = linkedSetOf<String>()
    val canonical = canonicalName

    if (canonical.isNotBlank()) {
        aliases += canonical
        aliases += canonical.substringAfterLast('.')

        val pkg = packageName.trim().trim('.')
        if (pkg.isNotBlank() && canonical.startsWith("$pkg.")) {
            val relative = canonical.removePrefix("$pkg.").trim('.')
            if (relative.isNotBlank()) {
                aliases += relative
            }
        }
    }

    val rawSimple = simpleName.trim()
    if (rawSimple.isNotBlank()) {
        aliases += rawSimple
        aliases += rawSimple.replace('$', '.')
        val terminal = rawSimple.substringAfterLast('$')
        if (terminal.isNotBlank()) {
            aliases += terminal
        }
    }

    return aliases.filter { it.isNotBlank() }.toSet()
}