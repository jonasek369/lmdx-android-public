package me.jonas.lmdx

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Intent

class Application : Application() {

    override fun onCreate() {
        super.onCreate()

        // Start the service when the app starts
        val intent = Intent(this, DownloadService::class.java)
        startService(intent)
    }

    override fun onTerminate() {
        super.onTerminate()

        // Stop the service when the app is closed (onTerminate rarely called on real devices)
        val intent = Intent(this, DownloadService::class.java)
        stopService(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }
}