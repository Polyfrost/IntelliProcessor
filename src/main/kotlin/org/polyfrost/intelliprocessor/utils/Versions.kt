package org.polyfrost.intelliprocessor.utils

object Versions {

    private val regex = "(?<major>\\d+)\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?".toRegex()

    fun makeComparable(version: String): Int? {
        val match = regex.find(version) ?: return null
        val groups = match.groups

        val major = groups["major"]?.value?.toInt() ?: return null
        val minor = groups["minor"]?.value?.toInt() ?: return null
        val patch = groups["patch"]?.value?.toInt() ?: 0

        return major * 10000 + minor * 100 + patch
    }

}
