package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertNotNull

internal class PersonalFinanceCategoryEnumTest {
    companion object {
        @JvmStatic
        fun provideFrom(): List<Arguments> {
            return listOf(
                Arguments.of(
//                    testName: String,
                    "Normal",
//                    input: PersonalFinanceCategory,
                    PersonalFinanceCategory("RENT_AND_UTILITIES", "RENT_AND_UTILITIES_GAS_AND_ELECTRICITY"),
//                    expectedResult: PersonalFinanceCategoryEnum?,
                    PersonalFinanceCategoryEnum.RENT_AND_UTILITIES_GAS_AND_ELECTRICITY,
//                    expectedException: Boolean,
                    false,
                ),
                Arguments.of(
//                    testName: String,
                    "Goofy edge case",
//                    input: PersonalFinanceCategory,
                    PersonalFinanceCategory("TRAVEL", "TRANSPORTATION_PUBLIC_TRANSIT"),
//                    expectedResult: PersonalFinanceCategoryEnum?,
                    PersonalFinanceCategoryEnum.TRANSPORTATION_PUBLIC_TRANSIT,
//                    expectedException: Boolean,
                    false,
                ),
                Arguments.of(
//                    testName: String,
                    "Invalid primary",
//                    input: PersonalFinanceCategory,
                    PersonalFinanceCategory("TACOS", "FAST_FOOD"),
//                    expectedResult: PersonalFinanceCategoryEnum?,
                    null,
//                    expectedException: Boolean,
                    true,
                ),
                Arguments.of(
//                    testName: String,
                    "Invalid detailed",
//                    input: PersonalFinanceCategory,
                    PersonalFinanceCategory("FOOD_AND_DRINK", "TACOS"),
//                    expectedResult: PersonalFinanceCategoryEnum?,
                    null,
//                    expectedException: Boolean,
                    true,
                ),
            )
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideFrom")
    fun from(
        testName: String,
        input: PersonalFinanceCategory,
        expectedResult: PersonalFinanceCategoryEnum?,
        expectedException: Boolean,
    ) {
        var actualException: Exception? = null
        var actual: PersonalFinanceCategoryEnum? = null
        try {
            actual = PersonalFinanceCategoryEnum.from(input)
        } catch (e: Exception) {
            actualException = e
        }

        if (expectedException) {
            assertNotNull(actualException)
        } else {
            assertEquals(expectedResult, actual)
        }
    }
}
