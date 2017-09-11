package ru.hyst329.trump.view

import com.beust.klaxon.*
import com.jfoenix.controls.JFXSlider
import com.jfoenix.controls.JFXTreeTableColumn
import com.jfoenix.controls.JFXTreeTableView
import com.jfoenix.controls.RecursiveTreeItem
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject
import javafx.application.Platform
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.util.Callback
import ru.hyst329.trump.logic.ChannelData
import tornadofx.*
import javax.sound.midi.*
import javafx.scene.control.TreeTableColumn
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import ru.hyst329.trump.logic.PlaylistEntry
import ru.hyst329.trump.logic.humanReadableDuration
import java.io.File
import javafx.util.Duration
import javafx.util.StringConverter
import ru.hyst329.trump.logic.SimplePlayer
import java.io.FileWriter
import java.nio.file.Files
import java.util.*
import kotlin.streams.toList


class MainWindow : View("My View") {
    override val root: VBox by fxml(hasControllerAttribute = true)
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
    val keySlider: JFXSlider by fxid()
    val tempoSlider: JFXSlider by fxid()
    val volumeSlider: JFXSlider by fxid()
    val posLabel: Label by fxid()
    val nameLabel: Label by fxid()
    val timeLabel: Label by fxid()
    val bpmLabel: Label by fxid()
    val channels = List(16, { i -> ChannelData(i + 1) }).observable()
    val playlist = mutableListOf<PlaylistEntry>().observable()
    val player = SimplePlayer(12)
    val fileChooser = FileChooser()
    val playlistChooser = FileChooser()
    val directoryChooser = DirectoryChooser()
    val updateTimer: Timer = Timer(true)

    init {
        keySlider.labelFormatter = (object : StringConverter<Double>() {
            override fun toString(`object`: Double?): String {
                return "%+1d".format(`object`?.toInt() ?: 0)
            }

            override fun fromString(string: String?): Double {
                return string?.toDouble() ?: 0.0
            }
        })

        tempoSlider.labelFormatter = (object : StringConverter<Double>() {
            override fun toString(`object`: Double?): String {
                return "\u00d7%4.2f".format(Math.pow(2.0, `object` ?: 0.0))
            }

            override fun fromString(string: String?): Double {
                return Math.log(string?.toDouble() ?: 0.0)  / Math.log(2.0)
            }
        })


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

        fileChooser.extensionFilters += FileChooser.ExtensionFilter("MIDI Files", "*.mid", "*.midi", "*.kar")
        playlistChooser.extensionFilters += FileChooser.ExtensionFilter("TRUMP Playlists", "*.trumppl")

        playlistTable.onDoubleClick {
            playerPlay()
        }

        updateTimer.schedule(object : TimerTask() {
            override fun run() {
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

        player.addListener {
            if (it.message is ShortMessage) {
                val msg = it.message as ShortMessage
                if (msg.command == ShortMessage.PROGRAM_CHANGE) {
                    channels[msg.channel].patchNumber = msg.data1 + 1
                }
                if (msg.command == ShortMessage.CONTROL_CHANGE) {
                    when (msg.data1) {
                        0 -> channels[msg.channel].bankNumber =
                                (channels[msg.channel].bankNumber and 127) or (msg.data2 * 128)
                        32 -> channels[msg.channel].bankNumber =
                                (channels[msg.channel].bankNumber and 16256) or msg.data2
                    }
                }
            }
            true
        }
    }

    fun playlistAddFiles() {
        val files = fileChooser.showOpenMultipleDialog(null)
        if (files != null) {
            playlist += List(files.size, { i ->
                PlaylistEntry(playlist.size + i + 1, files[i].path, files[i].name, Duration.ZERO)
            })
        }
    }

    fun playlistAddFolder() {
        val folder = directoryChooser.showDialog(null)
        if (folder != null) {
            val files = Files.walk(folder.toPath())
                    .filter { Files.isRegularFile(it) }
                    .map { it.toFile() }
                    .filter {
                        (it.extension in listOf("mid", "midi", "kar"))
                    }.toList()
            playlist += List(files.size, { i ->
                PlaylistEntry(playlist.size + i + 1, files[i].path, files[i].name, Duration.ZERO)
            })
            println("Total ${files.size} files")
        }
    }

    fun playlistNewList() {
        playlist.clear()
    }

    fun playlistLoadList() {
        val fileToLoad = playlistChooser.showOpenDialog(null)
        if (!fileToLoad.canRead()) {
            // TODO: Show error
            return
        }
        val data = Parser().parse(fileToLoad.inputStream())
        val array = data as JsonArray<JsonObject>
        playlist.clear()
        var ind = 1
        playlist += array.map {
            PlaylistEntry(ind++, it.string("filename") ?: "",
                    it.string("name") ?: "", Duration(0.0))
        }
    }

    fun playlistSaveList() {
        val fileToSave = playlistChooser.showSaveDialog(null)
//        if (!fileToSave.canWrite()) {
//            // TODO: Show error
//            val alert = Alert(Alert.AlertType.ERROR)
//            alert.title = "Cannot write to file"
//            alert.headerText = "Error"
//            alert.contentText = "File ${fileToSave.name} is not writable"
//            alert.showAndWait()
//            return
//        }
        val writer = FileWriter(fileToSave)
        val array = json {
            array(playlist.map {
                obj("filename" to it.filename, "name" to it.name)
            })
        }
        writer.write(array.toJsonString())
        writer.close()
    }

    fun playerPlay() {
        if (playlistTable.selectedItem == null) {
            playlistTable.selectFirst()
        }
        val pathname = playlistTable.selectedItem?.filename
        if (pathname != null) {
            posLabel.text = "${playlistTable.selectedItem?.position ?: 0} / ${playlist.size}"
            nameLabel.text = playlistTable.selectedItem?.name ?: ""
            playlistTable.selectedItem?.duration = ((player.sequence?.microsecondLength ?: 0) / 1000.0).millis
            player.sequence = MidiSystem.getSequence(File(pathname))
            player.play()
        }
    }

    fun playerStop() {
        if (player.isPlaying) {
            player.stop()
        }
    }


    fun playerPrev() {
        playerStop()
        if (playlistTable.selectionModel.selectedIndex > 0) {
            playlistTable.selectionModel.selectPrevious()
            playerPlay()
        }
    }

    fun playerNext() {
        playerStop()
        if (playlistTable.selectionModel.selectedIndex < playlist.lastIndex) {
            playlistTable.selectionModel.selectNext()
            playerPlay()
        }
    }

    fun cleanup() {
        player.stop()
    }
}
