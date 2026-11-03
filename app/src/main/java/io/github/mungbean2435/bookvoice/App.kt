package io.github.mungbean2435.bookvoice

import android.app.Application
import io.github.mungbean2435.bookvoice.data.db.Db
import io.github.mungbean2435.bookvoice.data.settings.CurrentBook
import io.github.mungbean2435.bookvoice.data.settings.Settings
import io.github.mungbean2435.bookvoice.data.settings.StartupSettings
import io.github.mungbean2435.bookvoice.playback.Speech

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Db.init(this)
        Settings.init(this)
        StartupSettings.init(this)
        CurrentBook.init(this)
        Speech.init(this)
    }
}
