package net.djvk.fireflyPlaidConnector2.versionManagement

import org.semver4j.Semver

object VersionComparison {
    /**
     * Returns true if [currentVersion] is greater than or equal to [minimumVersion] according
     *  to semantic versioning comparison rules, false otherwise.
     */
    fun isVersionSufficient(minimumVersion: String, currentVersion: String): Boolean {
        val minimum = Semver.parse(minimumVersion)
            ?: throw IllegalArgumentException("Unable to parse minimum Firefly version $minimumVersion")
        val current = Semver.parse(currentVersion)
            ?: throw IllegalArgumentException("Unable to parse current Firefly version $currentVersion")
        return current.isGreaterThanOrEqualTo(minimum)
    }
}