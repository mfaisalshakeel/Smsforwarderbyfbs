package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ForwardingRuleDao
import com.example.data.local.dao.SmsLogDao
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.SmsLogEntity

@Database(
    entities = [SmsLogEntity::class, ForwardingRuleEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsLogDao(): SmsLogDao
    abstract fun forwardingRuleDao(): ForwardingRuleDao

    companion object {
        private const val DB_NAME = "sms_forwarder_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 predates the released schema and its shape is not recoverable, so the two tables
         * are recreated at their v2 shape. Every later migration preserves user data.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `forwarding_rules`")
                db.execSQL("DROP TABLE IF EXISTS `sms_logs`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `forwarding_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `recipientEmail` TEXT NOT NULL,
                        `forwardSms` INTEGER NOT NULL,
                        `forwardNotifications` INTEGER NOT NULL,
                        `simSlot` INTEGER NOT NULL,
                        `senderFilterType` TEXT NOT NULL,
                        `senderFilterValue` TEXT NOT NULL,
                        `contentFilterType` TEXT NOT NULL,
                        `contentFilterValue` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sms_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `destinationType` TEXT NOT NULL,
                        `destinationTarget` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorMessage` TEXT,
                        `responsePayload` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `forwardedAt` INTEGER,
                        `source` TEXT NOT NULL,
                        `simSlot` INTEGER NOT NULL,
                        `ruleName` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 adds multi-destination rules, exclude filters, schedules and custom templates,
         * plus the retry-queue and de-duplication columns on the log. Existing rules and
         * logs are preserved: every new column carries a default.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val ruleColumns = listOf(
                    "`destinationType` TEXT NOT NULL DEFAULT 'EMAIL'",
                    "`destinationTarget` TEXT NOT NULL DEFAULT ''",
                    "`webhookFormat` TEXT NOT NULL DEFAULT 'JSON'",
                    "`webhookHeaders` TEXT NOT NULL DEFAULT ''",
                    "`telegramBotToken` TEXT NOT NULL DEFAULT ''",
                    "`appPackages` TEXT NOT NULL DEFAULT ''",
                    "`senderExcludeValue` TEXT NOT NULL DEFAULT ''",
                    "`contentExcludeValue` TEXT NOT NULL DEFAULT ''",
                    "`scheduleEnabled` INTEGER NOT NULL DEFAULT 0",
                    "`scheduleStartMinute` INTEGER NOT NULL DEFAULT 0",
                    "`scheduleEndMinute` INTEGER NOT NULL DEFAULT 1439",
                    "`scheduleDays` TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'",
                    "`useCustomTemplate` INTEGER NOT NULL DEFAULT 0",
                    "`subjectTemplate` TEXT NOT NULL DEFAULT ''",
                    "`bodyTemplate` TEXT NOT NULL DEFAULT ''"
                )
                ruleColumns.forEach { db.execSQL("ALTER TABLE `forwarding_rules` ADD COLUMN $it") }

                val logColumns = listOf(
                    "`ruleId` INTEGER",
                    "`packageName` TEXT",
                    "`contentHash` TEXT NOT NULL DEFAULT ''",
                    "`renderedSubject` TEXT",
                    "`renderedBody` TEXT",
                    "`nextAttemptAt` INTEGER"
                )
                logColumns.forEach { db.execSQL("ALTER TABLE `sms_logs` ADD COLUMN $it") }

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_logs_receivedAt` ON `sms_logs` (`receivedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_logs_status` ON `sms_logs` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_logs_contentHash` ON `sms_logs` (`contentHash`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
