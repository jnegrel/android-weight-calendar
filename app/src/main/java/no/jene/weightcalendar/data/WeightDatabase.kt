package no.jene.weightcalendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

// SQLite (the database inside Android) has no date type, so dates are stored as a number:
// the day count since 1970-01-01. Room calls these two functions to convert back and forth.
class Converters {
    @TypeConverter
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun fromEpochDay(day: Long): LocalDate = LocalDate.ofEpochDay(day)
}

// The database itself: which tables it contains, and its version number
// (bumped when the table layout changes, so Room knows to migrate old data).
@Database(entities = [WeightEntry::class], version = 1, exportSchema = false)
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
                    .build()
                    .also { instance = it }
            }
    }
}
