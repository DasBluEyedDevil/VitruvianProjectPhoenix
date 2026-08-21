package com.ninthlevel.phoenix.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.ninthlevel.phoenix.BuildConfig
import com.ninthlevel.phoenix.data.local.*
import com.ninthlevel.phoenix.data.local.seed.ExerciseIdRemapApplier
import com.ninthlevel.phoenix.data.local.seed.ExerciseIdRemapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an import operation
 */
data class ImportResult(
    val sessionsImported: Int,
    val sessionsSkipped: Int,
    val metricsImported: Int,
    val routinesImported: Int,
    val routinesSkipped: Int,
    val routineExercisesImported: Int,
    val programsImported: Int,
    val programsSkipped: Int,
    val programDaysImported: Int,
    val personalRecordsImported: Int,
    val personalRecordsSkipped: Int
) {
    val totalImported: Int
        get() = sessionsImported + metricsImported + routinesImported +
                routineExercisesImported + programsImported + programDaysImported +
                personalRecordsImported

    val totalSkipped: Int
        get() = sessionsSkipped + routinesSkipped + programsSkipped + personalRecordsSkipped
}

/**
 * Manages export and import of all workout data for backup/restore and migration
 */
@Singleton
class DataBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao,
    private val exerciseIdRemapApplier: ExerciseIdRemapApplier
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true  // Forward compatibility
        encodeDefaults = true
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Export all data to a JSON backup
     */
    suspend fun exportAllData(): BackupData = withContext(Dispatchers.IO) {
        Timber.d("Starting full data export")

        val sessions = workoutDao.getAllSessionsSync()
        val metrics = workoutDao.getAllMetricsSync()
        val routines = workoutDao.getAllRoutinesSync()
        val routineExercises = workoutDao.getAllRoutineExercisesSync()
        val programs = workoutDao.getAllProgramsSync()
        val programDays = workoutDao.getAllProgramDaysSync()
        val personalRecords = personalRecordDao.getAllPRsSync()

        Timber.d("Export counts: sessions=${sessions.size}, metrics=${metrics.size}, " +
                "routines=${routines.size}, routineExercises=${routineExercises.size}, " +
                "programs=${programs.size}, programDays=${programDays.size}, " +
                "personalRecords=${personalRecords.size}")

        BackupData(
            version = 1,
            exportedAt = dateFormat.format(Date()),
            appVersion = BuildConfig.VERSION_NAME,
            data = BackupContent(
                workoutSessions = sessions.map { it.toBackup() },
                workoutMetrics = metrics.map { it.toBackup() },
                routines = routines.map { it.toBackup() },
                routineExercises = routineExercises.map { it.toBackup() },
                weeklyPrograms = programs.map { it.toBackup() },
                programDays = programDays.map { it.toBackup() },
                personalRecords = personalRecords.map { it.toBackup() }
            )
        )
    }

    /**
     * Save backup data to Downloads folder and return the URI
     */
    suspend fun saveToDownloads(backup: BackupData): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(backup)
            val timestamp = fileDateFormat.format(Date())
            val fileName = "phoenix_backup_$timestamp.json"

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/ProjectPhoenix")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Failed to create file in Downloads")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }

                uri
            } else {
                // Android 9 and below - direct file access
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ProjectPhoenix"
                )
                downloadsDir.mkdirs()

                val file = File(downloadsDir, fileName)
                file.writeText(jsonString)

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            Timber.d("Backup saved to: $uri")
            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup")
            Result.failure(e)
        }
    }

    /**
     * Save backup to cache for sharing
     */
    suspend fun saveToCache(backup: BackupData): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(backup)
            val timestamp = fileDateFormat.format(Date())
            val fileName = "phoenix_backup_$timestamp.json"

            val file = File(context.cacheDir, fileName)
            file.writeText(jsonString)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Timber.d("Backup saved to cache: $uri")
            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup to cache")
            Result.failure(e)
        }
    }

    /**
     * Import data from a backup file URI
     * Uses "skip duplicates" strategy - existing records are not overwritten
     */
    suspend fun importFromUri(uri: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open file")

            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val backup = json.decodeFromString<BackupData>(jsonString)

            Timber.d("Importing backup version ${backup.version} from ${backup.appVersion}")

            if (backup.version > 1) {
                Timber.w("Backup version ${backup.version} is newer than supported (1)")
            }

            val data = ExerciseIdRemapper.remapBackupContent(
                backup.data,
                extraOldToNew = exerciseIdRemapApplier.deviceRemap()
            )

            // Get existing IDs for duplicate detection
            val existingSessionIds = workoutDao.getAllSessionIds().toSet()
            val existingRoutineIds = workoutDao.getAllRoutineIds().toSet()
            val existingProgramIds = workoutDao.getAllProgramIds().toSet()

            // Import sessions (skip existing)
            var sessionsImported = 0
            var sessionsSkipped = 0
            data.workoutSessions.forEach { session ->
                if (session.id !in existingSessionIds) {
                    workoutDao.insertSessionIgnore(session.toEntity())
                    sessionsImported++
                } else {
                    sessionsSkipped++
                }
            }

            // Import metrics (only for imported sessions)
            var metricsImported = 0
            val importedSessionIds = data.workoutSessions
                .filter { it.id !in existingSessionIds }
                .map { it.id }
                .toSet()

            data.workoutMetrics.forEach { metric ->
                if (metric.sessionId in importedSessionIds) {
                    workoutDao.insertMetricIgnore(metric.toEntity())
                    metricsImported++
                }
            }

            // Import routines (skip existing)
            var routinesImported = 0
            var routinesSkipped = 0
            data.routines.forEach { routine ->
                if (routine.id !in existingRoutineIds) {
                    workoutDao.insertRoutineIgnore(routine.toEntity())
                    routinesImported++
                } else {
                    routinesSkipped++
                }
            }

            // Import routine exercises (only for imported routines)
            var routineExercisesImported = 0
            val importedRoutineIds = data.routines
                .filter { it.id !in existingRoutineIds }
                .map { it.id }
                .toSet()

            data.routineExercises.forEach { exercise ->
                if (exercise.routineId in importedRoutineIds) {
                    workoutDao.insertRoutineExerciseIgnore(exercise.toEntity())
                    routineExercisesImported++
                }
            }

            // Import weekly programs (skip existing)
            var programsImported = 0
            var programsSkipped = 0
            data.weeklyPrograms.forEach { program ->
                if (program.id !in existingProgramIds) {
                    workoutDao.insertProgramIgnore(program.toEntity())
                    programsImported++
                } else {
                    programsSkipped++
                }
            }

            // Import program days (only for imported programs)
            var programDaysImported = 0
            val importedProgramIds = data.weeklyPrograms
                .filter { it.id !in existingProgramIds }
                .map { it.id }
                .toSet()

            data.programDays.forEach { day ->
                if (day.programId in importedProgramIds) {
                    workoutDao.insertProgramDayIgnore(day.toEntity())
                    programDaysImported++
                }
            }

            // Import personal records by logical key (exerciseId, workoutMode, prType).
            var personalRecordsImported = 0
            var personalRecordsSkipped = 0
            data.personalRecords.forEach { pr ->
                val incoming = pr.toEntity()
                val existing = personalRecordDao.getPR(
                    incoming.exerciseId,
                    incoming.workoutMode,
                    incoming.prType
                )
                if (existing == null) {
                    val rowId = personalRecordDao.insertPRIgnore(incoming.copy(id = 0))
                    if (rowId != -1L) personalRecordsImported++ else personalRecordsSkipped++
                } else {
                    val winner = ExerciseIdRemapper.mergePersonalRecords(
                        listOf(
                            ExerciseIdRemapper.PersonalRecordSnapshot(
                                id = existing.id,
                                exerciseId = existing.exerciseId,
                                weightPerCableKg = existing.weightPerCableKg,
                                reps = existing.reps,
                                timestamp = existing.timestamp,
                                workoutMode = existing.workoutMode,
                                prType = existing.prType,
                                volume = existing.volume
                            ),
                            ExerciseIdRemapper.PersonalRecordSnapshot(
                                id = incoming.id,
                                exerciseId = incoming.exerciseId,
                                weightPerCableKg = incoming.weightPerCableKg,
                                reps = incoming.reps,
                                timestamp = incoming.timestamp,
                                workoutMode = incoming.workoutMode,
                                prType = incoming.prType,
                                volume = incoming.volume
                            )
                        ),
                        oldToNew = emptyMap()
                    ).single()
                    if (winner.weightPerCableKg != existing.weightPerCableKg ||
                        winner.reps != existing.reps ||
                        winner.volume != existing.volume ||
                        winner.timestamp != existing.timestamp
                    ) {
                        personalRecordDao.upsertPR(
                            incoming.copy(id = existing.id)
                        )
                        personalRecordsImported++
                    } else {
                        personalRecordsSkipped++
                    }
                }
            }

            val result = ImportResult(
                sessionsImported = sessionsImported,
                sessionsSkipped = sessionsSkipped,
                metricsImported = metricsImported,
                routinesImported = routinesImported,
                routinesSkipped = routinesSkipped,
                routineExercisesImported = routineExercisesImported,
                programsImported = programsImported,
                programsSkipped = programsSkipped,
                programDaysImported = programDaysImported,
                personalRecordsImported = personalRecordsImported,
                personalRecordsSkipped = personalRecordsSkipped
            )

            Timber.d("Import complete: $result")
            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import backup")
            Result.failure(e)
        }
    }
}
