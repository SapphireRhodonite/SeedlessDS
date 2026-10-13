package com.seedlessds.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object LauncherIcon {
    private fun component(context: Context, alternative: Boolean) =
        ComponentName(context.packageName, context.packageName + if (alternative) ".AltIcon" else ".DefaultIcon")

    fun isAlternative(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context, true)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setAlternative(context: Context, alternative: Boolean) {
        val manager = context.packageManager
        val selected = component(context, alternative)
        val previous = component(context, !alternative)
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(previous, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP),
            ))
        } else {
            manager.setComponentEnabledSetting(selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            manager.setComponentEnabledSetting(previous, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
