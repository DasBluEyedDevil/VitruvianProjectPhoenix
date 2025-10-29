package com.example.vitruvianredux.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
            Log.d(TAG, "Starting exercise import from $ASSET_FILE")
            
            // Read JSON from assets
            val jsonString = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
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
                    Log.w(TAG, "Failed to parse exercise at index $i: ${e.message}")
                    // Continue with other exercises
                }
            }
            
            // Insert into database
            exerciseDao.insertAll(exercises)
            exerciseDao.insertVideos(videos)
            
            Log.d(TAG, "Successfully imported ${exercises.size} exercises with ${videos.size} videos")
            Result.success(exercises.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import exercises", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parse a single exercise from JSON
     */
    private fun parseExercise(json: JSONObject): ExerciseEntity {
        return ExerciseEntity(
            id = json.getString("id"),
            name = json.getString("name"),
            description = json.optString("description", ""),
            created = json.optString("created", ""),
            muscleGroups = json.optJSONArray("muscleGroups")?.toStringList() ?: "",
            muscles = json.optJSONArray("muscles")?.toStringList() ?: "",
            equipment = json.optJSONArray("equipment")?.toStringList() ?: "",
            movement = json.optString("movement").ifEmpty { null },
            sidedness = json.optString("sidedness").ifEmpty { null },
            grip = json.optString("grip").ifEmpty { null },
            gripWidth = json.optString("gripWidth").ifEmpty { null },
            minRepRange = json.optJSONObject("range")?.optDouble("minimum")?.toFloat(),
            popularity = json.optDouble("popularity", 0.0).toFloat(),
            archived = json.optBoolean("archived", false),
            isFavorite = false,
            timesPerformed = 0,
            lastPerformed = null
        )
    }
    
    /**
     * Parse videos for an exercise from JSON
     */
    private fun parseVideos(json: JSONObject, exerciseId: String): List<ExerciseVideoEntity> {
        val videosArray = json.optJSONArray("videos") ?: return emptyList()
        val videos = mutableListOf<ExerciseVideoEntity>()
        
        for (i in 0 until videosArray.length()) {
            try {
                val videoJson = videosArray.getJSONObject(i)
                val video = ExerciseVideoEntity(
                    id = 0, // Auto-generated
                    exerciseId = exerciseId,
                    angle = videoJson.optString("angle", videoJson.optString("name", "FRONT")),
                    videoUrl = videoJson.getString("video"),
                    thumbnailUrl = videoJson.getString("thumbnail")
                )
                videos.add(video)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse video at index $i for exercise $exerciseId: ${e.message}")
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
