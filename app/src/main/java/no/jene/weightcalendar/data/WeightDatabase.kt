package no.jene.weightcalendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

// SQLite (the database inside Android) has no date type, so dates are stored as a number:
// the day count since 1970-01-01. Room calls these two functions to convert back and forth.
class Converters {
    @TypeConverter
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun fromEpochDay(day: Long): LocalDate = LocalDate.ofEpochDay(day)
}

// Changes of the table layout between versions. Room runs them when the app is updated,
// before opening the database, so the existing data is kept.
// Without them, Room would refuse to open the old database and the app would crash.
//   1 -> 2: optional note on each day.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE weights ADD COLUMN note TEXT")
    }
}

// The database itself: which tables it contains, and its version number
// (bumped when the table layout changes, together with a migration above).
// exportSchema: Room saves a description of each version in app/schemas/ (kept in git),
// so future changes can be checked against it.
@Database(entities = [WeightEntry::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class WeightDatabase : RoomDatabase() {

    abstract fun weightDao(): WeightDao

    // "companion object" = what other languages call static members.
    // We keep a single database instance for the whole app.
    companion object {
        @Volatile private var instance: WeightDatabase? = null

        fun get(context: Context): WeightDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, WeightDatabase::class.java, "weights.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
