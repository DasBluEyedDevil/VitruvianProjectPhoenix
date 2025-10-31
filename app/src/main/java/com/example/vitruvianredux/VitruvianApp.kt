package com.example.vitruvianredux

import android.app.Application
import com.example.vitruvianredux.data.repository.ExerciseRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VitruvianApp : Application() {

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

        Timber.d("Vitruvian Trainer Control app initialized")

        // Import exercises on first launch (if database is empty)
        applicationScope.launch {
            try {
                Timber.d("Checking if exercise import is needed...")
                exerciseRepository.importExercises()
                    .onSuccess {
                        Timber.d("Exercise library ready")
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to import exercises")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during exercise import check")
            }
        }
    }
}

