package no.jene.weightcalendar.ui

import no.jene.weightcalendar.data.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseWeightTest {

    @Test
    fun acceptsDotAndComma() {
        assertEquals(82.4, parseWeight("82.4", WeightUnit.KG)!!, 1e-9)
        assertEquals(82.4, parseWeight("82,4", WeightUnit.KG)!!, 1e-9)
        assertEquals(82.4, parseWeight("82.43", WeightUnit.KG)!!, 1e-9)   // one decimal kept
    }

    @Test
    fun rejectsImplausibleValues() {
        assertNull(parseWeight("", WeightUnit.KG))
        assertNull(parseWeight("abc", WeightUnit.KG))
        assertNull(parseWeight("8", WeightUnit.KG))       // below 20 kg
        assertNull(parseWeight("500", WeightUnit.KG))     // above 400 kg
    }

    // Every weight typed in lb (one decimal) must show again exactly the same after being
    // stored in kg: otherwise saving a day unchanged would shift it a little.
    @Test
    fun poundsRoundTrip() {
        for (tenths in 441..8818) {
            val lb = tenths / 10.0
            val kg = parseWeight("%.1f".format(java.util.Locale.ROOT, lb), WeightUnit.LB)!!
            assertEquals(lb, Math.round(WeightUnit.LB.fromKg(kg) * 10) / 10.0, 1e-9)
        }
    }
}
