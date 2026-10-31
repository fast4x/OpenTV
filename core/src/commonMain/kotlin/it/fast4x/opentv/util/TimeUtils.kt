package it.fast4x.opentv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formatta un intervallo temporale come "20:15 – 21:00". */
fun formatHourRange(start: Long, stop: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${format.format(Date(start))} – ${format.format(Date(stop))}"
}

/** "Mancano X min" / "in onda da X min" per l'orario relativo. */
fun minutesUntil(timestamp: Long, now: Long = System.currentTimeMillis()): Int =
    TimeUnit.MILLISECONDS.toMinutes((timestamp - now).coerceAtLeast(0)).toInt()

/**
 * Chiave del giorno civile di un istante (anno, giorno dell'anno): usata per
 * raggruppare la programmazione EPG in sezioni "Oggi" / "Domani" / data.
 */
fun dayKey(timestamp: Long): Pair<Int, Int> {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return calendar.get(java.util.Calendar.YEAR) to
        calendar.get(java.util.Calendar.DAY_OF_YEAR)
}
