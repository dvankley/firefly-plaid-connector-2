package net.djvk.fireflyPlaidConnector2.versionManagement

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class VersionComparisonTest {
    companion object {
        @JvmStatic
        fun provideIsVersionSufficient(): List<Arguments> {
            return listOf(
                Arguments.of(
//                    testName: String,
                    "Base case true",
//                    minimumVersion: String,
                    "v6.1.2",
//                    currentVersion: String,
                    "v6.1.3",
//                    expectedResult: Boolean,
                    true,
                ),
                Arguments.of(
//                    testName: String,
                    "Base case false",
//                    minimumVersion: String,
                    "v6.1.2",
//                    currentVersion: String,
                    "v6.1.1",
//                    expectedResult: Boolean,
                    false,
                ),
                Arguments.of(
//                    testName: String,
                    "Base case equal",
//                    minimumVersion: String,
                    "v6.1.2",
//                    currentVersion: String,
                    "v6.1.2",
//                    expectedResult: Boolean,
                    true,
                ),
                Arguments.of(
//                    testName: String,
                    "Beta false",
//                    minimumVersion: String,
                    "v6.1.2",
//                    currentVersion: String,
                    "v6.1.2-beta",
//                    expectedResult: Boolean,
                    false,
                ),
                Arguments.of(
//                    testName: String,
                    "Beta true",
//                    minimumVersion: String,
                    "v6.1.2",
//                    currentVersion: String,
                    "v6.1.3-beta",
//                    expectedResult: Boolean,
                    true,
                ),
            )
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideIsVersionSufficient")
    fun isVersionSufficient(
        testName: String,
        minimumVersion: String,
        currentVersion: String,
        expectedResult: Boolean,
    ) {
        Assertions.assertEquals(expectedResult, VersionComparison.isVersionSufficient(minimumVersion, currentVersion))
    }
}
