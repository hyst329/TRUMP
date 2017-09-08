package ru.hyst329.trump.logic

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject
import javafx.util.Duration

class PlaylistEntry(var position: Int,
                    var filename: String,
                    var name: String,
                    var duration: Duration) : RecursiveTreeObject<PlaylistEntry>() {
}