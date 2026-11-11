package com.seedlessds.app

import android.app.Application
import com.seedlessds.app.filesystem.PathCache
import com.seedlessds.app.ra.RetroAchievements

class SeedlessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PathCache.setAppContext(applicationContext)
        AppLog.installCrashHandler()
        val process = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).runningAppProcesses
                ?.find { it.pid == android.os.Process.myPid() }?.processName
        if (process == packageName + ":phantom_primary" || process == packageName + ":phantom_peer") return
        AppLog.start(applicationContext, com.seedlessds.app.emu.SettingsRepo.load(applicationContext).appLogFile)
        RetroAchievements.init(applicationContext)
        RetroAchievements.tryTokenLogin()
    }
}
