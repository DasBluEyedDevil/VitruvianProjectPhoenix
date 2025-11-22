package com.example.vitruvianredux.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports exercises from JSON file in assets to Room database
 */
@Singleton
class ExerciseImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao
) {
    
    companion object {
        private const val TAG = "ExerciseImporter"
        private const val ASSET_FILE = "exercise_dump.json"
    }
    
    /**
     * Import exercises from assets/exercise_dump.json into the database
     * @return Result with count of exercises imported, or error
     */
    suspend fun importExercises(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting exercise import from $ASSET_FILE")

            // Read JSON from assets
            val jsonString = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            return@withContext importFromJsonString(jsonString)

        } catch (e: Exception) {
            Timber.e(e, "Failed to import exercises from assets")
            Result.failure(e)
        }
    }

    /**
     * Import exercises from a JSON string (for updates from network)
     * @param jsonString JSON array string containing exercise data
     * @param clearExisting If true, clears existing data before importing (default: true for updates)
     * @return Result with count of exercises imported, or error
     */
    suspend fun importFromJsonString(jsonString: String, clearExisting: Boolean = true): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(jsonString)

            val exercises = mutableListOf<ExerciseEntity>()
            val videos = mutableListOf<ExerciseVideoEntity>()

            // Parse each exercise
            for (i in 0 until jsonArray.length()) {
                val jsonExercise = jsonArray.getJSONObject(i)

                try {
                    val exercise = parseExercise(jsonExercise)
                    exercises.add(exercise)

                    // Parse videos for this exercise
                    val exerciseVideos = parseVideos(jsonExercise, exercise.id)
                    videos.addAll(exerciseVideos)
                } catch (e: Exception) {
                    Timber.w("Failed to parse exercise at index $i: ${e.message}")
                    // Continue with other exercises
                }
            }

            // Clear existing data if requested (for updates)
            if (clearExisting) {
                exerciseDao.deleteAllVideos()
                exerciseDao.deleteAll()
            }

            // Insert into database
            exerciseDao.insertAll(exercises)
            exerciseDao.insertVideos(videos)

            Timber.d("Successfully imported ${exercises.size} exercises with ${videos.size} videos")
            Result.success(exercises.size)

        } catch (e: Exception) {
            Timber.e(e, "Failed to import exercises from JSON string")
            Result.failure(e)
        }
    }
    
    /**
     * Parse a single exercise from JSON
     */
    private fun parseExercise(json: JSONObject): ExerciseEntity {
        val sidedness = json.optString("sidedness").ifEmpty { null }

        return ExerciseEntity(
            id = json.getString("id"),
            name = json.getString("name"),
            description = json.optString("description", ""),
            created = json.optString("created", ""),
            muscleGroups = json.optJSONArray("muscleGroups")?.toStringList() ?: "",
            muscles = json.optJSONArray("muscles")?.toStringList() ?: "",
            equipment = json.optJSONArray("equipment")?.toStringList() ?: "",
            movement = json.optString("movement").ifEmpty { null },
            sidedness = sidedness,
            grip = json.optString("grip").ifEmpty { null },
            gripWidth = json.optString("gripWidth").ifEmpty { null },
            minRepRange = json.optJSONObject("range")?.optDouble("minimum")?.toFloat(),
            popularity = json.optDouble("popularity", 0.0).toFloat(),
            archived = json.optBoolean("archived", false),
            isFavorite = false,
            timesPerformed = 0,
            lastPerformed = null,
            aliases = json.optJSONArray("aliases")?.toStringList() ?: "",
            defaultCableConfig = mapSidednessToConfig(sidedness)
        )
    }

    /**
     * Map JSON sidedness field to Vitruvian cable configuration
     * - bilateral (both arms/legs) → DOUBLE (both cables)
     * - unilateral (one arm/leg) → SINGLE (one cable)
     * - alternating (one at a time) → EITHER (user choice)
     */
    private fun mapSidednessToConfig(sidedness: String?): String {
        return when (sidedness?.lowercase()) {
            "bilateral" -> "DOUBLE"
            "unilateral" -> "SINGLE"
            "alternating" -> "EITHER"
            else -> "DOUBLE" // Safe default for exercises without sidedness data
        }
    }
    
    /**
     * Parse videos for an exercise from JSON
     * Includes both angle demonstrations and tutorial video if present
     */
    private fun parseVideos(json: JSONObject, exerciseId: String): List<ExerciseVideoEntity> {
        val videos = mutableListOf<ExerciseVideoEntity>()

        // Parse angle demonstration videos
        val videosArray = json.optJSONArray("videos")
        if (videosArray != null) {
            for (i in 0 until videosArray.length()) {
                try {
                    val videoJson = videosArray.getJSONObject(i)
                    val video = ExerciseVideoEntity(
                        id = 0, // Auto-generated
                        exerciseId = exerciseId,
                        angle = videoJson.optString("angle", videoJson.optString("name", "FRONT")),
                        videoUrl = videoJson.getString("video"),
                        thumbnailUrl = videoJson.getString("thumbnail"),
                        isTutorial = false
                    )
                    videos.add(video)
                } catch (e: Exception) {
                    Timber.w("Failed to parse video at index $i for exercise $exerciseId: ${e.message}")
                }
            }
        }

        // Parse tutorial video if present (separate from angle videos)
        val tutorialJson = json.optJSONObject("tutorial")
        if (tutorialJson != null) {
            try {
                val tutorial = ExerciseVideoEntity(
                    id = 0, // Auto-generated
                    exerciseId = exerciseId,
                    angle = "TUTORIAL",
                    videoUrl = tutorialJson.getString("video"),
                    thumbnailUrl = tutorialJson.getString("thumbnail"),
                    isTutorial = true
                )
                videos.add(tutorial)
            } catch (e: Exception) {
                Timber.w("Failed to parse tutorial video for exercise $exerciseId: ${e.message}")
            }
        }

        return videos
    }
    
    /**
     * Convert JSONArray to comma-separated string
     */
    private fun JSONArray.toStringList(): String {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(getString(i))
        }
        return list.joinToString(",")
    }
    
    /**
     * Check if exercises have been imported
     */
    suspend fun hasExercises(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Query for count
            val count = exerciseDao.getAllExercises()
            // This is a simple check - in production you might want a better way
            false // Placeholder - would need a count query
        } catch (e: Exception) {
            false
        }
    }
}
