package no.jene.weightcalendar.data

// The unit weights are shown and typed in. The database, the CSV files and the colour
// thresholds always use kg: only what the user sees is converted.
enum class WeightUnit(val label: String, private val perKg: Double) {
    KG("kg", 1.0),
    LB("lb", 2.2046226218);   // 1 kg = 2.2046... lb (exact definition: 1 lb = 0.45359237 kg)

    fun fromKg(kg: Double) = kg * perKg
    fun toKg(value: Double) = value / perKg

    // "110.3 kg" / "243.2 lb", and "+0.8 kg" / "+1.8 lb" for a change.
    fun format(kg: Double) = "%.1f %s".format(fromKg(kg), label)
    fun formatChange(kg: Double) = "%+.1f %s".format(fromKg(kg), label)
}
