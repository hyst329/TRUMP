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
                if (isPlaying) stop()
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
                mcsPosition = 0
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
    val effectiveBPM: Double
        get() = currentTempoBPM * tempoFactor
    var isPlaying = false
        private set
    var tempoCache = MidiUtils.TempoCache()
    var playThread: Thread? = null
    var listeners: Array<(MidiEvent) -> Boolean> = emptyArray()
    var drumChannels: BooleanArray = kotlin.BooleanArray(16, { it == 9 }) // channel 10 (9 + 1) is for drums by default

    var tempoFactor: Double = 1.0
    var volume: Int = 128
    var key: Int = 0

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
                mcsPosition = ((System.nanoTime() - start) / 1000 * tempoFactor).toLong()
                if (tickPosition >= track[event].tick) {
                    // send the message
                    if (track[event].message is MetaMessage) {
                        val meta = track[event].message as MetaMessage
                        if (MidiUtils.isMetaTempo(meta)) {
                            currentTempoMPQ = MidiUtils.getTempoMPQ(meta).toLong()
                        }
                    } else if (track[event].message is ShortMessage) {
                        var message = track[event].message as ShortMessage
                        if ((message.command == ShortMessage.NOTE_ON || message.command == ShortMessage.NOTE_OFF)
                                && !drumChannels[message.channel]) {
                            var note = message.data1
                            note += key
                            if (note < 0) note = 0
                            if (note > 127) note = 127
                            val newMessage = ShortMessage()
                            newMessage.setMessage(message.command, message.channel, note, message.data2)
                            message = newMessage
                        }
                        device?.receiver!!.send(message, -1)
                    }
                    for (listener in listeners) {
                        if (listener(track[event]))
                            break
                    }
                    event++
                }
                try {
                    Thread.sleep(1)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    fun pause() {
        isPlaying = false
        playThread?.interrupt()
        device?.close()
    }

    fun stop() {
        pause()
        mcsPosition = 0
    }

    fun addListener(listener: (MidiEvent) -> Boolean) {
        listeners += listener
    }

    fun allSoundsOff() {
        val message = ShortMessage()
        for (i in 0..15) {
            message.setMessage(ShortMessage.CONTROL_CHANGE, i, 120, 0)
            device?.receiver!!.send(message, -1)
        }
    }

}
