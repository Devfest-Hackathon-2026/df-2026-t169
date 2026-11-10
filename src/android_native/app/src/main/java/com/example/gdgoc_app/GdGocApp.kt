package com.example.gdgoc_app

import android.app.Application
import com.example.gdgoc_app.config.AppConfig
import com.example.gdgoc_app.di.ServiceLocator

class GdGocApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = AppConfig.loadFromAssets(this)
        ServiceLocator.init(config, this)
    }
}
