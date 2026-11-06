package com.joebywan.daybook

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joebywan.daybook.platform.DailyReminder
import com.joebywan.daybook.platform.appLanguageContext
import com.joebywan.daybook.platform.migrateAppLanguage
import com.joebywan.daybook.ui.DaybookApp

class DaybookApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(appLanguageContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        migrateAppLanguage(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(appLanguageContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The reminder's alarm, set again from the stored setting: a clock or time-zone change since can have moved it.
        if (savedInstanceState == null) DailyReminder.reschedule(applicationContext)
        // DaybookApp applies the theme itself, since the player's light/dark choice is a setting.
        setContent { DaybookApp() }
    }
}
