@file:Suppress("ktlint:standard:no-empty-file")

package dev.traumatisedturkey.trialtracker.data

// Placeholder: this file previously held MigrationTestHelper-based tests for MIGRATION_5_6
// through MIGRATION_8_9 (renaming, table rebuilds, singleton-id collapse, nullable relaxation -
// see git history for the removed test bodies and TrialDatabase.kt's history comment for why
// those migrations themselves were dropped).
//
// As of this flattening, TrialDatabase has no migrations registered and a single exported
// schema snapshot (app/schemas/.../9.json). There is nothing to test yet.
//
// When the next version bump adds a real Migration (hand-written or AutoMigration), bring the
// test pattern back here:
//
// @RunWith(AndroidJUnit4::class)
// class TrialDatabaseMigrationTest {
//     @get:Rule
//     val helper: MigrationTestHelper = MigrationTestHelper(
//         InstrumentationRegistry.getInstrumentation(),
//         TrialDatabase::class.java,
//         emptyList(),
//         FrameworkSQLiteOpenHelperFactory(),
//     )
//
//     @Test
//     fun migrateNToN1_<describesTheChange>() {
//         helper.createDatabase(TEST_DB, N).apply {
//             // seed rows matching N.json's shape
//             close()
//         }
//         val db = helper.runMigrationsAndValidate(TEST_DB, N + 1, true, MIGRATION_N_N1)
//         // assert data survived/transformed as expected, including any FK-relevant columns
//         // (see MIGRATION_6_7's entries.trialId rewrite for why that check matters)
//     }
// }
//
// private const val TEST_DB = "migration-test"
