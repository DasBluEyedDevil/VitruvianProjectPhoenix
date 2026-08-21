package com.ninthlevel.phoenix

import android.app.Application
import com.ninthlevel.phoenix.data.repository.ExerciseRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PhoenixApp : Application() {

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("Project Phoenix initialized")

        applicationScope.launch {
            try {
                exerciseRepository.seedDefaultExercises()
            } catch (e: Exception) {
                Timber.e(e, "Error during exercise seed")
            }
        }
    }
}

