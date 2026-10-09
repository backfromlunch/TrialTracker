package dev.traumatisedturkey.trialtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Entry::class, TrialRecord::class, Settings::class],
    version = 9,
    // History prior to v9 has been dropped.
    // All deployed clients were confirmed running at v9 before the drop.
    exportSchema = true,
)
abstract class TrialDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    abstract fun trialDao(): TrialDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        @Suppress("ktlint:standard:property-naming")
        @Volatile
        private var INSTANCE: TrialDatabase? = null

        fun getInstance(context: Context): TrialDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room
                .databaseBuilder(
                    context.applicationContext,
                    TrialDatabase::class.java,
                    "trial_database",
                )
                // No fallbackToDestructiveMigration: Entry holds real user data, and it would
                // drop all tables on any unhandled version bump. Every version bump must go
                // through an explicit AutoMigration or Migration that preserves `entries`.
                //
                // No .addMigrations() call: there are currently zero migrations to register.
                // The next version bump must add one, or existing installs will crash on
                // upgrade instead of silently wiping data.
                //
                // Settings is not seeded here via Callback.onCreate, since that only fires on
                // a brand-new DB file, not on upgrade. See SettingsDao.getOrCreateDefault(),
                // which seeds lazily on first read regardless of install vs. upgrade.
                .build()
                .also { INSTANCE = it }
        }
    }
}
