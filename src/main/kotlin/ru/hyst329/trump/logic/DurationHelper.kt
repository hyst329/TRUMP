package ru.hyst329.trump.logic

import java.lang.Math.round
import javafx.util.Duration
import java.lang.Math.max

fun humanReadableDuration(d: Duration, precision: Int): String {
    val prec = max(0, precision)
    val min: Int = d.toMinutes().toInt()
    val sec: Double = d.toSeconds() % 60
    return "%02d:%0${if (prec > 0) prec + 3 else prec + 2}.${prec}f".format(min, sec)
}