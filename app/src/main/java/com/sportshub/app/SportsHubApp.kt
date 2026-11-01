package com.sportshub.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class SportsHubApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Coil uses this loader for every image in the app. */
    override fun newImageLoader(): ImageLoader = container.imageLoader
}
