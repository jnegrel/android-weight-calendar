package no.jene.weightcalendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightUnitTest {

    @Test
    fun conversions() {
        assertEquals(1.0, WeightUnit.LB.toKg(2.2046226218), 1e-9)
        assertEquals(220.462, WeightUnit.LB.fromKg(100.0), 1e-3)
        assertEquals(75.0, WeightUnit.KG.fromKg(75.0), 0.0)
    }
}
