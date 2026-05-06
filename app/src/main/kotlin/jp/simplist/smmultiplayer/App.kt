package jp.simplist.smmultiplayer

import android.app.Application
import jp.simplist.smmultiplayer.trial.TrialManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        TrialManager.initialize(this)
    }
}
