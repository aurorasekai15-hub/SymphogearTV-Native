package com.symphogear.tv

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDexApplication

class App: MultiDexApplication() {
    companion object {
        private lateinit var current: Application

        val context: Context
            get() = current.applicationContext

        fun runOnUiThread(task: Runnable) {
            Handler(Looper.getMainLooper()).post(task)
        }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
    }
}
