package com.alfredang.sgcarpark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val SingaporeCenter = Coordinate(1.3521, 103.8198)

data class CarparkMapUiState(
    val query: String = "",
    val carparks: List<Carpark> = emptyList(),
    val selectedCarpark: Carpark? = null,
    val userLocation: Coordinate? = null,
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val mapTarget: Coordinate = SingaporeCenter,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: LocalTime? = null,
) {
    val totalAvailableLots: Int = carparks.sumOf { it.availableLots }
    val visibleCarparks: Int = carparks.size
    val lastUpdatedText: String? = lastUpdated?.format(DateTimeFormatter.ofPattern("h:mm a"))
    val selectedDistanceText: String? =
        selectedCarpark?.let { carpark ->
            userLocation?.let { carpark.distanceTo(it).formatDistance() }
        }

    val filteredCarparks: List<Carpark>
        get() {
            val trimmed = query.trim()
            return if (trimmed.isBlank()) {
                carparks
            } else {
                carparks.filter { it.matches(trimmed) }
            }
        }
}

class CarparkMapViewModel(
    application: Application,
    private val client: LTADataMallClient = LTADataMallClient(),
    private val searchService: SearchService = SearchService(application),
    private val locationService: LocationService = LocationService(application),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        client = LTADataMallClient(),
        searchService = SearchService(application),
        locationService = LocationService(application),
    )

    private val _uiState = MutableStateFlow(CarparkMapUiState())
    val uiState: StateFlow<CarparkMapUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        refreshCarparks()
    }

    fun refreshCarparks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { client.fetchCarparks() }
                .onSuccess { carparks ->
                    val sorted = carparks.sortedWith(
                        compareByDescending<Carpark> { it.availableLots }
                            .thenBy { it.title },
                    )
                    _uiState.update {
                        it.copy(
                            carparks = sorted,
                            selectedCarpark = it.selectedCarpark ?: sorted.firstOrNull(),
                            mapTarget = it.selectedCarpark?.coordinate ?: sorted.firstOrNull()?.coordinate ?: SingaporeCenter,
                            isLoading = false,
                            lastUpdated = LocalTime.now(),
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (BuildConfig.LTA_ACCOUNT_KEY.isBlank()) {
                                "Add your LTA DataMall account key in local.properties."
                            } else {
                                "Car park availability is not available right now."
                            },
                        )
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, isSearching = query.isNotBlank()) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }

            val localMatches = _uiState.value.carparks.filter { it.matches(trimmed) }.take(5)
            runCatching { searchService.search(trimmed) }
                .onSuccess { places ->
                    _uiState.update {
                        it.copy(
                            selectedCarpark = localMatches.firstOrNull() ?: it.selectedCarpark,
                            mapTarget = localMatches.firstOrNull()?.coordinate ?: places.firstOrNull()?.coordinate ?: it.mapTarget,
                            searchResults = places,
                            isSearching = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                }
        }
    }

    fun selectCarpark(carpark: Carpark) {
        _uiState.update {
            it.copy(
                selectedCarpark = carpark,
                mapTarget = carpark.coordinate,
                searchResults = emptyList(),
            )
        }
    }

    fun selectPlace(result: PlaceSearchResult) {
        val nearest = _uiState.value.carparks.nearestTo(result.coordinate)
        _uiState.update {
            it.copy(
                selectedCarpark = nearest,
                mapTarget = result.coordinate,
                searchResults = emptyList(),
            )
        }
    }

    fun findNearestCarpark(onPermissionNeeded: () -> Unit) {
        if (!locationService.hasLocationPermission()) {
            onPermissionNeeded()
            return
        }

        viewModelScope.launch {
            runCatching { locationService.currentLocation() }
                .onSuccess { location ->
                    val nearest = location?.let { _uiState.value.carparks.nearestTo(it) }
                    _uiState.update {
                        it.copy(
                            userLocation = location ?: it.userLocation,
                            selectedCarpark = nearest ?: it.selectedCarpark,
                            mapTarget = nearest?.coordinate ?: location ?: it.mapTarget,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(errorMessage = "Could not read your current location.") }
                }
        }
    }

    fun setUserLocation(coordinate: Coordinate?) {
        _uiState.update { it.copy(userLocation = coordinate) }
    }
}
