package com.example.vitruvianredux.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.local.ExerciseVideoEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for Exercise Library
 */
data class ExerciseLibraryUiState(
    val exercises: List<ExerciseEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedMuscleGroups: Set<String> = emptySet(),
    val selectedEquipment: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isImporting: Boolean = false,
    val showFavoritesOnly: Boolean = false
)

/**
 * Available muscle group filters
 */
enum class MuscleGroupFilter(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    LEGS("Legs"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core"),
    FULL_BODY("Full Body")
}

/**
 * Available equipment filters
 */
enum class EquipmentFilter(val displayName: String) {
    BODYWEIGHT("Bodyweight"),
    CABLE("Cable"),
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell")
}

/**
 * ViewModel for Exercise Library screen
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ExerciseLibraryUiState())
    val uiState: StateFlow<ExerciseLibraryUiState> = _uiState.asStateFlow()
    
    private val searchQuery = MutableStateFlow("")
    private val selectedMuscleGroups = MutableStateFlow<Set<String>>(emptySet())
    private val selectedEquipment = MutableStateFlow<Set<String>>(emptySet())
    private val showFavoritesOnly = MutableStateFlow(false)
    
    init {
        // Combine all filters and load exercises
        combine(
            searchQuery,
            selectedMuscleGroups,
            selectedEquipment,
            showFavoritesOnly
        ) { query, muscles, equipment, favoritesOnly ->
            ExerciseFilters(query, muscles, equipment, favoritesOnly)
        }.flatMapLatest { filters ->
            loadExercises(filters)
        }.onEach { exercises ->
            _uiState.update { it.copy(exercises = exercises, isLoading = false) }
        }.catch { e ->
            Timber.e(e, "Error loading exercises")
            _uiState.update { it.copy(error = e.message, isLoading = false) }
        }.launchIn(viewModelScope)
    }
    
    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    /**
     * Toggle muscle group filter
     */
    fun toggleMuscleGroupFilter(muscleGroup: String) {
        selectedMuscleGroups.update { current ->
            if (current.contains(muscleGroup)) {
                current - muscleGroup
            } else {
                current + muscleGroup
            }
        }
        _uiState.update { it.copy(selectedMuscleGroups = selectedMuscleGroups.value) }
    }
    
    /**
     * Toggle equipment filter
     */
    fun toggleEquipmentFilter(equipment: String) {
        selectedEquipment.update { current ->
            if (current.contains(equipment)) {
                current - equipment
            } else {
                current + equipment
            }
        }
        _uiState.update { it.copy(selectedEquipment = selectedEquipment.value) }
    }
    
    /**
     * Toggle favorite for an exercise
     */
    fun toggleFavorite(exerciseId: String) {
        viewModelScope.launch {
            try {
                exerciseRepository.toggleFavorite(exerciseId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite")
                _uiState.update { it.copy(error = "Failed to update favorite") }
            }
        }
    }
    
    /**
     * Toggle showing favorites only
     */
    fun toggleShowFavoritesOnly() {
        showFavoritesOnly.update { !it }
        _uiState.update { it.copy(showFavoritesOnly = showFavoritesOnly.value) }
    }
    
    /**
     * Clear all filters
     */
    fun clearFilters() {
        searchQuery.value = ""
        selectedMuscleGroups.value = emptySet()
        selectedEquipment.value = emptySet()
        showFavoritesOnly.value = false
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedMuscleGroups = emptySet(),
                selectedEquipment = emptySet(),
                showFavoritesOnly = false
            )
        }
    }
    
    /**
     * Import exercises from assets
     */
    fun importExercises() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            
            exerciseRepository.importExercises()
                .onSuccess {
                    _uiState.update { it.copy(isImporting = false) }
                    Timber.d("Exercises imported successfully")
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = "Failed to import exercises: ${e.message}"
                        )
                    }
                    Timber.e(e, "Failed to import exercises")
                }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Load exercises based on filters
     */
    private fun loadExercises(filters: ExerciseFilters): Flow<List<ExerciseEntity>> {
        return when {
            // Show favorites only
            filters.showFavoritesOnly -> {
                exerciseRepository.getFavorites()
            }
            // Search query takes precedence
            filters.searchQuery.isNotBlank() -> {
                exerciseRepository.searchExercises(filters.searchQuery)
            }
            // Filter by muscle group
            filters.muscleGroups.isNotEmpty() -> {
                // Get exercises for each selected muscle group and combine
                combine(
                    filters.muscleGroups.map { muscleGroup ->
                        exerciseRepository.filterByMuscleGroup(muscleGroup)
                    }
                ) { exerciseLists ->
                    // Flatten and remove duplicates
                    exerciseLists.flatMap { it }.distinctBy { it.id }
                }
            }
            // Filter by equipment
            filters.equipment.isNotEmpty() -> {
                // Get exercises for each selected equipment and combine
                combine(
                    filters.equipment.map { equipment ->
                        exerciseRepository.filterByEquipment(equipment)
                    }
                ) { exerciseLists ->
                    // Flatten and remove duplicates
                    exerciseLists.flatMap { it }.distinctBy { it.id }
                }
            }
            // No filters, show all
            else -> {
                exerciseRepository.getAllExercises()
            }
        }.map { exercises ->
            // Apply additional filters if needed
            exercises.filter { exercise ->
                // If both muscle and equipment filters are selected, show only exercises that match both
                val matchesMuscle = filters.muscleGroups.isEmpty() || 
                    filters.muscleGroups.any { it in exercise.muscleGroups }
                val matchesEquipment = filters.equipment.isEmpty() || 
                    filters.equipment.any { it in exercise.equipment }
                
                matchesMuscle && matchesEquipment
            }
        }
    }
    
    /**
     * Get videos for an exercise
     */
    suspend fun getExerciseVideos(exerciseId: String): List<ExerciseVideoEntity> {
        return exerciseRepository.getVideos(exerciseId)
    }
    
    /**
     * Data class to hold all filter states
     */
    private data class ExerciseFilters(
        val searchQuery: String,
        val muscleGroups: Set<String>,
        val equipment: Set<String>,
        val showFavoritesOnly: Boolean
    )
}