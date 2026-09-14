package com.sap.codelab.repository

import android.app.Application
import com.sap.codelab.di.AppContainer

/**
 * Extension of the Android Application class.
 */
internal class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
