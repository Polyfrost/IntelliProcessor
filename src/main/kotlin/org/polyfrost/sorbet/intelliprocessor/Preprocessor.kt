package org.polyfrost.sorbet.intelliprocessor

val ALLOWED_TYPES = listOf("JAVA", "KOTLIN")

enum class PreprocessorState { NONE, IF, ELSE }
