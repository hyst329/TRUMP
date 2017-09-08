package ru.hyst329.trump.logic

import com.sun.media.sound.MidiUtils
import java.io.InputStream
import javax.sound.midi.*
import kotlin.concurrent.thread

//import com.sun.media.sound.RealTimeSequencer


class SimplePlayer(deviceInfo: MidiDevice.Info) {
    var sequence: Sequence? = null
        get
        set(value) {
            if (value != null) {
                if(isPlaying) stop()
                val s = Sequence(value.divisionType, value.resolution)
                val t = s.createTrack()
                for (track in value.tracks) {
                    for (i in 0 until track.size()) {
                        t.add(track[i])
                    }
                }
                field = s
                tempoCache = MidiUtils.TempoCache()
                tempoCache.refresh(field)
            }
        }
    var device: MidiDevice? = null
        get
        set(value) {
            if (value is Sequencer || value is Synthesizer || value == null) {
                throw IllegalArgumentException("device parameter must be a MIDI port")
            }
            if (value.maxTransmitters > 0) {
                throw IllegalArgumentException("MIDI port must be OUT, not IN")
            }
            field = value
        }
    var tickPosition: Long = 0
        get() = MidiUtils.microsecond2tick(sequence, mcsPosition, tempoCache)
    var mcsPosition: Long = 0
    var currentTempoMPQ: Long = 0
        get
        private set
    var currentTempoBPM: Double = 0.0
        get() = 6e7 / currentTempoMPQ
    var isPlaying = false
        private set
    var tempoCache = MidiUtils.TempoCache()
    var playThread: Thread? = null
    var listeners: Array<(MidiEvent) -> Boolean> = emptyArray()

    init {
        device = MidiSystem.getMidiDevice(deviceInfo)
    }

    constructor(deviceIndex: Int) : this(MidiSystem.getMidiDeviceInfo()[deviceIndex])

    fun play() {
        isPlaying = true
        device?.open()
        playThread = thread(start = true, isDaemon = false, priority = 8) {
            val track = sequence!!.tracks[0]
            var event = 0
            while (track[event].tick < tickPosition) event++
            val start = System.nanoTime() - mcsPosition * 1000
            while (event < track.size()) {
                mcsPosition = (System.nanoTime() - start) / 1000
                if (tickPosition >= track[event].tick) {
                    // send the message
                    if (track[event].message is MetaMessage) {
                        val meta = track[event].message as MetaMessage
                        if (MidiUtils.isMetaTempo(meta)) {
                            currentTempoMPQ = MidiUtils.getTempoMPQ(meta).toLong()
                        }
                    } else {
                        device?.receiver!!.send(track[event].message, -1)
                    }
                    for (listener in listeners) {
                        if (listener(track[event]))
                            break
                    }
                    event++
                }
                Thread.sleep(1)
            }
        }
    }

    fun pause() {
        isPlaying = false
        playThread?.stop() // TODO: Replace with non-deprecated method
        device?.close()
    }

    fun stop() {
        pause()
        mcsPosition = 0
    }

    fun addListener(listener: (MidiEvent) -> Boolean) {
        listeners += listener
    }

}
