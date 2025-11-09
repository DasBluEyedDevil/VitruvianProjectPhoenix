package com.example.vitruvianredux.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for exporting workout data to CSV format
 */
object CsvExporter {

    // Create new instances per operation to ensure thread safety
    private fun getDateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private fun getFileDateFormat() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Export personal records to CSV file
     */
    fun exportPersonalRecords(
        context: Context,
        personalRecords: List<PersonalRecord>,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<Uri> {
        return try {
            val timestamp = getFileDateFormat().format(Date())
            val fileName = "personal_records_$timestamp.csv"
            val file = File(context.cacheDir, fileName)

            FileWriter(file).use { writer ->
                // Write header
                writer.append("Exercise,Weight Per Cable,Unit,Reps,Workout Mode,Date\n")

                // Write data
                personalRecords.forEach { pr ->
                    val exerciseName = exerciseNames[pr.exerciseId] ?: "Unknown Exercise"
                    val weight = formatWeight(pr.weightPerCableKg, weightUnit)
                    val date = getDateFormat().format(Date(pr.timestamp))

                    writer.append("\"$exerciseName\",")
                    writer.append("$weight,")
                    writer.append("${weightUnit.name},")
                    writer.append("${pr.reps},")
                    writer.append("\"${pr.workoutMode}\",")
                    writer.append("\"$date\"\n")
                }
            }

            // Get URI for the file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export personal records to CSV")
            Result.failure(e)
        }
    }

    /**
     * Export workout history to CSV file
     */
    fun exportWorkoutHistory(
        context: Context,
        workoutSessions: List<WorkoutSession>,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<Uri> {
        return try {
            val timestamp = getFileDateFormat().format(Date())
            val fileName = "workout_history_$timestamp.csv"
            val file = File(context.cacheDir, fileName)

            FileWriter(file).use { writer ->
                // Write header
                writer.append("Date,Exercise,Mode,Weight Per Cable,Unit,Progression,Target Reps,Actual Reps,Warmup Reps,Duration (min),Just Lift,Echo Level,Eccentric Load\n")

                // Write data
                workoutSessions.forEach { session ->
                    val exerciseName = session.exerciseId?.let { exerciseNames[it] } ?: "Unknown"
                    val date = getDateFormat().format(Date(session.timestamp))
                    val weight = formatWeight(session.weightPerCableKg, weightUnit)
                    val progression = formatWeight(session.progressionKg, weightUnit)
                    val durationMin = session.duration / 60000 // Convert ms to minutes

                    writer.append("\"$date\",")
                    writer.append("\"$exerciseName\",")
                    writer.append("\"${session.mode}\",")
                    writer.append("$weight,")
                    writer.append("${weightUnit.name},")
                    writer.append("$progression,")
                    writer.append("${session.reps},")
                    writer.append("${session.workingReps},")
                    writer.append("${session.warmupReps},")
                    writer.append("$durationMin,")
                    writer.append("${session.isJustLift},")
                    writer.append("${session.echoLevel},")
                    writer.append("${session.eccentricLoad}%\n")
                }
            }

            // Get URI for the file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export workout history to CSV")
            Result.failure(e)
        }
    }

    /**
     * Share CSV file using Android's share sheet
     */
    fun shareCSV(context: Context, uri: Uri, fileName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export CSV"))
    }

    /**
     * Export all PRs grouped by exercise with progression data
     */
    fun exportPRProgression(
        context: Context,
        personalRecords: List<PersonalRecord>,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<Uri> {
        return try {
            val timestamp = getFileDateFormat().format(Date())
            val fileName = "pr_progression_$timestamp.csv"
            val file = File(context.cacheDir, fileName)

            // Group PRs by exercise
            val prsByExercise = personalRecords.groupBy { it.exerciseId }

            FileWriter(file).use { writer ->
                // Write header
                writer.append("Exercise,Date,Weight Per Cable,Unit,Reps,Workout Mode,Improvement %\n")

                // Write data grouped by exercise
                prsByExercise.forEach { (exerciseId, prs) ->
                    val exerciseName = exerciseNames[exerciseId] ?: "Unknown Exercise"
                    val sortedPRs = prs.sortedBy { it.timestamp }

                    sortedPRs.forEachIndexed { index, pr ->
                        val date = getDateFormat().format(Date(pr.timestamp))
                        val weight = formatWeight(pr.weightPerCableKg, weightUnit)

                        // Calculate improvement from previous PR
                        val improvement = if (index > 0) {
                            val previousWeight = sortedPRs[index - 1].weightPerCableKg
                            val improvementPercent = ((pr.weightPerCableKg - previousWeight) / previousWeight * 100).toInt()
                            if (improvementPercent > 0) "+$improvementPercent%" else "$improvementPercent%"
                        } else {
                            "First PR"
                        }

                        writer.append("\"$exerciseName\",")
                        writer.append("\"$date\",")
                        writer.append("$weight,")
                        writer.append("${weightUnit.name},")
                        writer.append("${pr.reps},")
                        writer.append("\"${pr.workoutMode}\",")
                        writer.append("$improvement\n")
                    }
                }
            }

            // Get URI for the file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export PR progression to CSV")
            Result.failure(e)
        }
    }
}
