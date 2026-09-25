package com.example.util

import com.example.data.local.entity.DestinationType
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.LogStatus
import com.example.data.local.entity.SmsLogEntity
import com.example.data.preferences.ForwarderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private val rule = ForwardingRuleEntity(
        id = 7,
        name = "Bank alerts",
        destinationType = DestinationType.TELEGRAM,
        destinationTarget = "123456",
        telegramBotToken = "SECRET-BOT-TOKEN",
        appPackages = "com.whatsapp",
        contentFilterValue = "OTP"
    )

    private val settings = ForwarderSettings(
        senderEmailAccount = "me@example.com",
        senderAppPassword = "SECRET-PASSWORD",
        logRetentionDays = 30
    )

    @Test
    fun `export round trips a rule`() {
        val json = BackupManager.exportToJson(listOf(rule), settings)
        val result = BackupManager.importFromJson(json, ForwarderSettings())

        assertEquals(1, result.rules.size)
        val restored = result.rules.first()
        assertEquals("Bank alerts", restored.name)
        assertEquals(DestinationType.TELEGRAM, restored.destinationType)
        assertEquals("123456", restored.destinationTarget)
        assertEquals("com.whatsapp", restored.appPackages)
        assertEquals("OTP", restored.contentFilterValue)
    }

    @Test
    fun `secrets are never written to the backup file`() {
        val json = BackupManager.exportToJson(listOf(rule), settings)
        assertFalse("bot token leaked into the backup", json.contains("SECRET-BOT-TOKEN"))
        assertFalse("mail password leaked into the backup", json.contains("SECRET-PASSWORD"))
    }

    @Test
    fun `settings are restored on top of the current ones`() {
        val json = BackupManager.exportToJson(emptyList(), settings)
        val result = BackupManager.importFromJson(json, ForwarderSettings())
        assertNotNull(result.settings)
        assertEquals(30, result.settings?.logRetentionDays)
    }

    @Test
    fun `a malformed file reports an error instead of throwing`() {
        val result = BackupManager.importFromJson("this is not json", ForwarderSettings())
        assertNotNull(result.error)
        assertTrue(result.rules.isEmpty())
    }

    @Test
    fun `csv quotes and escapes every field`() {
        val log = SmsLogEntity(
            sender = "HBL",
            body = "He said \"hello\"\nand left",
            destinationType = DestinationType.EMAIL,
            destinationTarget = "to@example.com",
            status = LogStatus.SUCCESS
        )
        val csv = BackupManager.exportLogsToCsv(listOf(log))
        val lines = csv.trim().lines()

        assertEquals(2, lines.size)
        // The embedded quote is doubled and the newline is flattened, so the row stays one line.
        assertTrue(csv.contains("\"He said \"\"hello\"\" and left\""))
    }

    @Test
    fun `suggested file names carry the right extension`() {
        assertTrue(BackupManager.suggestedBackupName().endsWith(".json"))
        assertTrue(BackupManager.suggestedCsvName().endsWith(".csv"))
    }
}
