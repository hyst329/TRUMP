package ru.hyst329.trump.view

import com.jfoenix.controls.JFXTreeTableColumn
import com.jfoenix.controls.JFXTreeTableView
import com.jfoenix.controls.RecursiveTreeItem
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.util.Callback
import ru.hyst329.trump.logic.ChannelData
import tornadofx.*
import javax.sound.midi.*
import javafx.scene.control.TreeTableColumn
import javafx.scene.input.MouseEvent
import javafx.stage.FileChooser
import ru.hyst329.trump.logic.PlaylistEntry
import ru.hyst329.trump.logic.humanReadableDuration
import java.io.File
import java.io.InputStream
import javafx.util.Duration
import ru.hyst329.trump.logic.SimplePlayer
import java.util.*


class MainWindow : View("My View") {
    override val root: VBox by fxml()
    val channelsTable: JFXTreeTableView<ChannelData> by fxid()
    val playlistTable: JFXTreeTableView<PlaylistEntry> by fxid()
    val channelsNumberColumn: JFXTreeTableColumn<ChannelData, Int> by fxid()
    val channelsPatchColumn: JFXTreeTableColumn<ChannelData, Int> by fxid()
    val channelsBankColumn: JFXTreeTableColumn<ChannelData, Int> by fxid()
    val channelsInstrumentColumn: JFXTreeTableColumn<ChannelData, String> by fxid()
    val playlistPosColumn: JFXTreeTableColumn<PlaylistEntry, Int> by fxid()
    val playlistFilenameColumn: JFXTreeTableColumn<PlaylistEntry, String> by fxid()
    val playlistNameColumn: JFXTreeTableColumn<PlaylistEntry, String> by fxid()
    val playlistDurColumn: JFXTreeTableColumn<PlaylistEntry, String> by fxid()
    val posLabel: Label by fxid()
    val nameLabel: Label by fxid()
    val timeLabel: Label by fxid()
    val bpmLabel: Label by fxid()
    val channels = List(16, { i -> ChannelData(i + 1) }).observable()
    val playlist = mutableListOf<PlaylistEntry>().observable()
    var sequencer: Sequencer = MidiSystem.getSequencer(false) //MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[5]) as Sequencer
    val player = SimplePlayer(14)
    val chooser = FileChooser()
    val updateTimer: Timer = Timer(true)

    init {
        //sequencer.transmitter.receiver = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[12]).receiver

        channelsTable.root = RecursiveTreeItem(channels, RecursiveTreeObject<ChannelData>::getChildren)
        channelsTable.isShowRoot = false
        channelsNumberColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<ChannelData, Int> ->
            ReadOnlyObjectWrapper(param.value.value.channelNumber)
        }
        channelsPatchColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<ChannelData, Int> ->
            ReadOnlyObjectWrapper(param.value.value.patchNumber)
        }
        channelsBankColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<ChannelData, Int> ->
            ReadOnlyObjectWrapper(param.value.value.bankNumber)
        }
        channelsInstrumentColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<ChannelData, String> ->
            ReadOnlyObjectWrapper(param.value.value.instrument)
        }

        playlistTable.root = RecursiveTreeItem(playlist, RecursiveTreeObject<PlaylistEntry>::getChildren)
        playlistTable.isShowRoot = false

        playlistPosColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<PlaylistEntry, Int> ->
            ReadOnlyObjectWrapper(param.value.value.position)
        }
        playlistFilenameColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<PlaylistEntry, String> ->
            ReadOnlyObjectWrapper(param.value.value.filename)
        }
        playlistNameColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<PlaylistEntry, String> ->
            ReadOnlyObjectWrapper(param.value.value.name)
        }
        playlistDurColumn.cellValueFactory = Callback { param: TreeTableColumn.CellDataFeatures<PlaylistEntry, String> ->
            ReadOnlyObjectWrapper(humanReadableDuration(param.value.value.duration, 0))
        }

        chooser.extensionFilters += FileChooser.ExtensionFilter("MIDI Files", "*.mid", "*.midi", "*.kar")

        playlistTable.onDoubleClick {
            val pathname = playlistTable.selectedItem?.filename
            if (pathname != null) {
                //sequencer.sequence = MidiSystem.getSequence(File(pathname))
                posLabel.text = "${playlistTable.selectedItem?.position ?: 0} / ${playlist.size}"
                nameLabel.text = playlistTable.selectedItem?.name ?: ""
                //playlistTable.selectedItem?.duration = (sequencer.microsecondLength / 1000.0).millis
                //sequencer.open()
                //sequencer.start()
                player.sequence = MidiSystem.getSequence(File(pathname))
                player.play()
                //MidiSystem.write(player.sequence, 0, File("C:/test/test.mid"))
            }
        }

        sequencer.addControllerEventListener( ControllerEventListener {
            val newBank = if (it.data1 == 0) it.data2 shl 7 else it.data2
            var newBankNumber = channels[it.channel].bankNumber and (if (it.data1 == 0) 0x00FF else 0xFF00)
            newBankNumber = newBankNumber or newBank
            println("Channel ${it.channel+1}: bank ${channels[it.channel].bankNumber} to ${newBankNumber}")
            channels[it.channel].bankNumber = newBankNumber
        }, intArrayOf(0, 32))

        sequencer.addMetaEventListener {
            // stop on end-of-track
            if (it.type == 47) {
                sequencer.close()
            }
        }

        updateTimer.schedule(object: TimerTask() {
            override fun run() {
//                if (sequencer.isRunning) {
//                    val elapsed = (sequencer.microsecondPosition / 1000.0).millis
//                    val total = (sequencer.microsecondLength / 1000.0).millis
//                    val bpm = sequencer.tempoInBPM.toDouble()
//                    for (c in 0..15) {
//                        //channels[c].patchNumber = (sequencer as Synthesizer).channels[c].program
//                    }
//                    Platform.runLater {
//                        timeLabel.text = "${humanReadableDuration(elapsed, 1)} / ${humanReadableDuration(total, 3)}"
//                        bpmLabel.text = "BPM: %5.1f".format(bpm)
//                    }
//                }
                if (player.isPlaying) {
                    val elapsed = (player.mcsPosition / 1000.0).millis
                    val total = (player.sequence!!.microsecondLength / 1000.0).millis
                    val bpm = player.currentTempoBPM
                    Platform.runLater {
                        timeLabel.text = "${humanReadableDuration(elapsed, 1)} / ${humanReadableDuration(total, 3)}"
                        bpmLabel.text = "BPM: %5.1f".format(bpm)
                    }
                }
            }
        }, 100, 100)
    }

    fun playlistAddFiles() {
        val files = chooser.showOpenMultipleDialog(null)
        if (files != null) {
            playlist += List(files.size, { i ->
                PlaylistEntry(playlist.size + i + 1, files[i].path, files[i].name, Duration.ZERO)
            })
        }
    }

    fun cleanup() {
//        sequencer.stop()
//        sequencer.close()
        player.stop()
    }
}
