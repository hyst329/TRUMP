package ru.hyst329.trump.logic

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import tornadofx.*

class ChannelData(var channelNumber: Int = 1,
                  var patchNumber: Int = 0,
                  var bankNumber: Int = 0,
                  var instrument: String = "[Unassigned]",
                  var ccData: IntArray = IntArray(128))
    : RecursiveTreeObject<ChannelData>() {
    override fun toString(): String {
        return "ChannelData[channel=${channelNumber}, patch=${patchNumber}, bank=${bankNumber}]"
    }

    var channelProperty: IntegerProperty = SimpleIntegerProperty(channelNumber)
    var patchProperty: IntegerProperty = SimpleIntegerProperty(patchNumber)
    var bankProperty: IntegerProperty = SimpleIntegerProperty(bankNumber)
}

class ChannelDataModel : ItemViewModel<ChannelData>() {}
