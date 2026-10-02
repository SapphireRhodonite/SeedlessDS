package com.seedlessds.app

import android.app.Application
import com.seedlessds.app.filesystem.PathCache
import com.seedlessds.app.ra.RetroAchievements

class SeedlessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PathCache.setAppContext(applicationContext)
        AppLog.installCrashHandler()
        AppLog.start(applicationContext, com.seedlessds.app.emu.SettingsRepo.load(applicationContext).appLogFile)
        RetroAchievements.init(applicationContext)
        RetroAchievements.tryTokenLogin()
    }
}
