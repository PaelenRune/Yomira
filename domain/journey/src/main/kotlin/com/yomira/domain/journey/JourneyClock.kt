package com.yomira.domain.journey

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Injectable time source. Production uses the device clock; tests pin a fixed
 * instant and zone so streaks, night-owl hours and month boundaries are
 * deterministic without sleeping.
 */
interface JourneyClock {
    val zone: ZoneId
    fun nowMillis(): Long
    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()
    fun todayEpochDay(): Long = today().toEpochDay()
    fun hourOfDay(): Int = Instant.ofEpochMilli(nowMillis()).atZone(zone).hour
}

class SystemJourneyClock @Inject constructor() : JourneyClock {
    override val zone: ZoneId = ZoneId.systemDefault()
    override fun nowMillis(): Long = System.currentTimeMillis()
}
