package io.github.indusy55.featuremanager

import android.app.Application
import io.github.indusy55.featuremanager.data.FeatureRepository
import io.github.indusy55.featuremanager.service.FrameworkService

class FeatureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FeatureRepository.get(this)
        FrameworkService.registerOnce()
    }
}
