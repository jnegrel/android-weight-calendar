package no.jene.weightcalendar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

// Tests of the estimated-day rule and of the CSV round trip, on a real Room database.
// They run on a device/emulator (they need Android's SQLite), in a temporary in-memory
// database: the app's own data is never touched.
@RunWith(AndroidJUnit4::class)
class WeightRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: WeightDatabase
    private lateinit var repository: WeightRepository

    private val d1 = LocalDate.of(2026, 1, 1)
    private fun day(n: Long) = d1.plusDays(n - 1)   // day(1) = 1 January, day(4) = 4 January

    // @Before / @After run around every @Test: each test starts with an empty database.
    @Before
    fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java).build()
        repository = WeightRepository(db)
    }

    @After
    fun closeDatabase() = db.close()

    private fun entry(n: Long) = runBlocking { db.weightDao().get(day(n)) }

    @Test
    fun loggingAfterAGapFillsItWithAStraightLine() = runBlocking {
        repository.setWeight(day(1), 100.0, null)
        repository.setWeight(day(4), 97.0, null)
        assertEquals(99.0, entry(2)!!.weightKg, 1e-9)
        assertEquals(98.0, entry(3)!!.weightKg, 1e-9)
        assertEquals(true, entry(2)!!.estimated)
        assertEquals(false, entry(4)!!.estimated)
    }

    @Test
    fun editingAnEstimatedDayRedrawsItsNeighbours() = runBlocking {
        repository.setWeight(day(1), 100.0, null)
        repository.setWeight(day(4), 97.0, null)
        repository.setWeight(day(2), 99.5, "note")
        assertEquals(false, entry(2)!!.estimated)
        assertEquals("note", entry(2)!!.note)
        assertEquals(98.25, entry(3)!!.weightKg, 1e-9)   // halfway between 99.5 and 97.0
    }

    @Test
    fun deletingAMeasuredDayBetweenTwoMakesItEstimated() = runBlocking {
        repository.setWeight(day(1), 100.0, null)
        repository.setWeight(day(2), 95.0, "gone")
        repository.setWeight(day(3), 98.0, null)
        repository.delete(day(2))
        assertEquals(99.0, entry(2)!!.weightKg, 1e-9)
        assertEquals(true, entry(2)!!.estimated)
        assertNull(entry(2)!!.note)                      // the note leaves with the day
    }

    @Test
    fun deletingTheLastDayRemovesTheEstimatesBeforeIt() = runBlocking {
        repository.setWeight(day(1), 100.0, null)
        repository.setWeight(day(4), 97.0, null)
        repository.delete(day(4))
        assertNull(entry(2))
        assertNull(entry(3))
        assertNull(entry(4))
    }

    @Test
    fun exportThenImportGivesTheSameDays() = runBlocking {
        repository.setWeight(day(1), 100.0, "Restaurant, pizza; \"heavy\"")
        repository.setWeight(day(5), 98.3, null)
        val settings = Settings(context)
        val out = ByteArrayOutputStream()
        repository.exportCsv(out, settings)

        // Import into a second, empty database. The # setting lines are removed first, so
        // the test doesn't write into the app's real settings.
        val csv = out.toString().lines().filterNot { it.startsWith("#") }.joinToString("\n")
        val other = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java).build()
        val count = WeightRepository(other).importCsv(ByteArrayInputStream(csv.toByteArray()), settings)
        assertEquals(5, count)
        // The CSV keeps 2 decimals: measured days come back exactly, estimated ones (like
        // 99.575 kg) within 0.005 kg, far below what the app shows (0.1).
        val before = db.weightDao().getAll()
        val after = other.weightDao().getAll()
        assertEquals(before.map { it.copy(weightKg = 0.0) }, after.map { it.copy(weightKg = 0.0) })
        before.zip(after).forEach { (b, a) ->
            assertEquals(b.weightKg, a.weightKg, if (b.estimated) 0.005 + 1e-9 else 0.0)
        }
        other.close()
    }
}
