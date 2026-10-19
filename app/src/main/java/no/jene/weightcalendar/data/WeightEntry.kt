package no.jene.weightcalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

// One row of the database table "weights": the weight for one day.
// @Entity tells Room to create a table from this class; each property becomes a column.
@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey val date: LocalDate,   // one entry per day, so the date is the unique key
    val weightKg: Double,
    val estimated: Boolean = false,    // true = filled in by interpolation, not measured
    val note: String? = null,          // optional free text (added in database version 2)
)
