package ru.hyst329.trump.app

import ru.hyst329.trump.view.MainWindow
import tornadofx.*


class MyApp: App(MainWindow::class, Styles::class) {
    override fun stop() {
        val window = find(primaryView, scope) as MainWindow
        window.cleanup()
        super.stop()
    }
}