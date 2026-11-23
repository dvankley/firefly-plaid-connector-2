package net.djvk.fireflyPlaidConnector2.smoke

import net.djvk.fireflyPlaidConnector2.transactions.plaidExternalId
import net.djvk.fireflyPlaidConnector2.transactions.plaidTransferReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@ExtendWith(OutputCaptureExtension::class)
class LiveSmokeTest {
    private val accounts = listOf(LiveSmokeAccount("account-a", "item-a"), LiveSmokeAccount("account-b", "item-b"))
    private val fireflyAccounts = listOf(101, 102)

    @Test
    fun `a stale mapping fails before starting the smoke stack`(@TempDir directory: Path, output: CapturedOutput) {
        val profile = directory.resolve("profile.yml")
        profile.writeText("""
            fireflyPlaidConnector2:
              plaid:
                url: https://sandbox.plaid.com
                clientId: test-client
                secret: test-secret
              accounts:
                - plaidAccountId: account-a
                  plaidItemAccessToken: item-a
                - plaidAccountId: account-b
                  plaidItemAccessToken: item-b
        """.trimIndent())
        val checkedItems = mutableListOf<String>()

        val status = SmokeTestCommand.run(
            arrayOf("--live", "--profile=$profile", "--batch-lookback-days=14", "--polled-lookback-days=4"),
            activeAccounts = { _, token ->
                checkedItems.add(token)
                if (token == "item-a") setOf("different-account") else setOf("account-b")
            },
            createSmokeRun = { fail("The smoke stack started before mappings were validated") },
        )

        assertEquals(1, status)
        assertEquals(listOf("item-a", "item-b"), checkedItems)
        assertContains(output.err, "Mapping #1: account is absent from its Plaid Item.")
        for (secret in listOf("item-a", "account-a", "test-secret")) assertFalse(secret in output.all)
    }

    @Test
    fun `preflight queries each Item once and reports every invalid mapping`() {
        val checkedItems = mutableListOf<String>()
        val failure = assertFailsWith<SmokeFailure> {
            validateLiveSmokeMappings(accounts + LiveSmokeAccount("account-c", "item-a")) { token ->
                checkedItems.add(token)
                if (token == "item-a") setOf("account-a") else setOf("different-account")
            }
        }

        assertEquals(listOf("item-a", "item-b"), checkedItems)
        assertContains(failure.message.orEmpty(), "Mapping #2:")
        assertContains(failure.message.orEmpty(), "Mapping #3:")
    }

    @Test
    fun `a transfer verifies coverage for both accounts`() {
        val eligible = mapOf(
            "account-a" to listOf(LiveSmokeTransaction("out", outgoing = true)),
            "account-b" to listOf(LiveSmokeTransaction("in", outgoing = false)),
        )
        val report = mutableListOf<String>()

        assertTrue(reportLiveSmokeCoverage("Poll", accounts, fireflyAccounts, eligible, transfer(), report::add))
        assertEquals(listOf(
            "Poll mapping #1: 1 eligible, 1 matched Firefly writes, verified.",
            "Poll mapping #2: 1 eligible, 1 matched Firefly writes, verified.",
        ), report)
    }

    @Test
    fun `a write on the wrong account fails even when another mapping passes`() {
        val eligible = mapOf(
            "account-a" to listOf(LiveSmokeTransaction("out", outgoing = true)),
            "account-b" to listOf(LiveSmokeTransaction("in", outgoing = false)),
        )
        val failure = assertFailsWith<SmokeFailure> {
            reportLiveSmokeCoverage("Batch", accounts, fireflyAccounts, eligible, transfer(destination = 103)) {}
        }

        assertEquals("Batch is missing expected Firefly writes for mapping #2.", failure.message)
    }

    @Test
    fun `quiet accounts make write coverage inconclusive`() {
        val eligible = mapOf("account-a" to listOf(LiveSmokeTransaction("out", outgoing = true)))
        val report = mutableListOf<String>()

        assertFalse(reportLiveSmokeCoverage("Poll", accounts, fireflyAccounts, eligible, transfer(), report::add))
        assertContains(report.last(), "mapping #2: 0 eligible, 0 matched Firefly writes, inconclusive")
    }

    private fun transfer(destination: Int = 102): Map<String, SmokeTransaction> {
        val split = smokeJson.createObjectNode()
            .put("source_id", "101")
            .put("destination_id", destination.toString())
            .put("internal_reference", plaidTransferReference("out"))
        return mapOf(plaidExternalId("in") to SmokeTransaction("1", split))
    }
}
