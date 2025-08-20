package com.example.photoswooper

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.photoswooper.data.database.MIGRATION_2_3
import com.example.photoswooper.data.database.MediaStatusDatabase
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MigrationTest {
    private val TEST_DB = "test_db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaStatusDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            // Database has schema version 2. Insert some data using SQL queries.
            execSQL("CREATE TABLE media_status_ver_2(`fileHash` TEXT NOT NULL," +
                    " `mediaStoreId` INTEGER NOT NULL," +
                    " `status` TEXT NOT NULL," +
                    " `size` INTEGER NOT NULL," +
                    " `dateModified` INTEGER NOT NULL," +
                    " `snoozedUntil` INTEGER," +
                    " PRIMARY KEY(`fileHash`) )"
            )
            execSQL(
                "INSERT INTO media_status_ver_2 (fileHash," +
                        " mediaStoreId," +
                        " status," +
                        " size," +
                        " dateModified," +
                        " snoozedUntil)" +
                        "VALUES (0, 0, 'KEEP', 12, 19202392, NULL)"
            )
            execSQL(
                "INSERT INTO media_status_ver_2 (fileHash," +
                        " mediaStoreId," +
                        " status," +
                        " size," +
                        " dateModified," +
                        " snoozedUntil)" +
                        "VALUES (1, 1, 'SNOOZE', 12, 19202392, 120923928392839)"
            )
            execSQL(
                "INSERT INTO media_status_ver_2 (fileHash," +
                        " mediaStoreId," +
                        " status," +
                        " size," +
                        " dateModified," +
                        " snoozedUntil)" +
                        "VALUES (2, 2, 'DELETE', 12, 123213, NULL)"
            )

            // Prepare for the next version.
            close()
        }

        // Re-open the database with version 2 and provide
        // MIGRATION_1_2 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_2_3)

        // MigrationTestHelper automatically verifies the schema changes,
        // but you need to validate that the data was migrated properly.
    }
}