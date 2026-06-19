package com.alfredang.sgcarpark

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SGCarparkTheme {
                SGCarparkApp()
            }
        }
    }
}

@Composable
private fun SGCarparkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0B6E4F),
            secondary = Color(0xFF386FA4),
            tertiary = Color(0xFFB45309),
            surface = Color(0xFFF7FAF7),
            surfaceVariant = Color(0xFFE8EFE9),
            background = Color(0xFFF7FAF7),
        ),
        content = content,
    )
}

@Composable
private fun SGCarparkApp(viewModel: CarparkMapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            viewModel.findNearestCarpark(onPermissionNeeded = {})
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CarparkMap(
                state = state,
                onSelectCarpark = viewModel::selectCarpark,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchPanel(
                    state = state,
                    onQueryChange = viewModel::updateQuery,
                    onSelectCarpark = viewModel::selectCarpark,
                    onSelectPlace = viewModel::selectPlace,
                )
                OverviewStrip(
                    availableLots = state.filteredCarparks.sumOf { it.availableLots },
                    carparkCount = state.filteredCarparks.size,
                    updatedAt = state.lastUpdatedText,
                    isLoading = state.isLoading,
                    onRefresh = viewModel::refreshCarparks,
                )
                if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) {
                    InlineStatus("Add googleMapsApiKey in local.properties to display Google Maps.")
                }
                state.errorMessage?.let { InlineStatus(it) }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElevatedButton(
                    onClick = {
                        viewModel.findNearestCarpark {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("Nearby")
                }
            }

            state.selectedCarpark?.let { carpark ->
                BottomCarparkPanel(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    carpark = carpark,
                    distanceText = state.selectedDistanceText,
                    onDirections = {
                        val uri = Uri.parse("google.navigation:q=${carpark.coordinate.latitude},${carpark.coordinate.longitude}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
            }
        }
    }
}

@Composable
private fun CarparkMap(
    state: CarparkMapUiState,
    onSelectCarpark: (Carpark) -> Unit,
) {
    val target = state.mapTarget.toLatLng()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, 11f)
    }

    LaunchedEffect(state.mapTarget) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(state.mapTarget.toLatLng(), 15f))
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            compassEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        ),
        properties = MapProperties(isMyLocationEnabled = state.userLocation != null),
    ) {
        state.filteredCarparks.take(350).forEach { carpark ->
            key(carpark.id) {
                Marker(
                    state = MarkerState(position = carpark.coordinate.toLatLng()),
                    title = carpark.title,
                    snippet = "${carpark.availableLots} ${carpark.lotTypeName.lowercase()} available",
                    onClick = {
                        onSelectCarpark(carpark)
                        false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchPanel(
    state: CarparkMapUiState,
    onQueryChange: (String) -> Unit,
    onSelectCarpark: (Carpark) -> Unit,
    onSelectPlace: (PlaceSearchResult) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true,
                label = { Text("Search postal code, mall, street, or car park") },
            )
            if (state.isSearching) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Searching Singapore", style = MaterialTheme.typography.bodySmall)
                }
            }
            SearchResults(
                carparks = state.filteredCarparks.take(4),
                places = state.searchResults.take(3),
                query = state.query,
                onSelectCarpark = onSelectCarpark,
                onSelectPlace = onSelectPlace,
            )
        }
    }
}

@Composable
private fun SearchResults(
    carparks: List<Carpark>,
    places: List<PlaceSearchResult>,
    query: String,
    onSelectCarpark: (Carpark) -> Unit,
    onSelectPlace: (PlaceSearchResult) -> Unit,
) {
    if (query.isBlank()) return

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (carparks.isNotEmpty()) {
            item { ResultSectionTitle("Car parks") }
        }
        items(carparks, key = { "carpark-${it.id}" }) { carpark ->
            ResultRow(
                title = carpark.title,
                subtitle = "${carpark.availableLots} available • ${carpark.subtitle}",
                onClick = { onSelectCarpark(carpark) },
            )
        }
        if (places.isNotEmpty()) {
            item { ResultSectionTitle("Places") }
        }
        items(places, key = { "place-${it.name}-${it.coordinate.latitude}-${it.coordinate.longitude}" }) { place ->
            ResultRow(
                title = place.name,
                subtitle = place.address,
                onClick = { onSelectPlace(place) },
            )
        }
    }
}

@Composable
private fun ResultSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewStrip(
    availableLots: Int,
    carparkCount: Int,
    updatedAt: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$availableLots lots available",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append("$carparkCount car parks")
                        if (updatedAt != null) append(" • Updated $updatedAt")
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text(if (isLoading) "Loading" else "Refresh", color = Color.White)
            }
        }
    }
}

@Composable
private fun InlineStatus(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFF4D6),
        tonalElevation = 2.dp,
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = Color(0xFF634300),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BottomCarparkPanel(
    modifier: Modifier = Modifier,
    carpark: Carpark,
    distanceText: String?,
    onDirections: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        carpark.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        carpark.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        carpark.availableLots.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = availabilityColor(carpark.availableLots),
                        fontWeight = FontWeight.Bold,
                    )
                    Text("available", style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Lot type", carpark.lotTypeName)
                InfoPill("Agency", carpark.agency.ifBlank { "Unknown" })
                InfoPill("Distance", distanceText ?: "Enable location")
            }
            Button(onClick = onDirections, modifier = Modifier.fillMaxWidth()) {
                Text("Directions")
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun availabilityColor(availableLots: Int): Color = when {
    availableLots >= 80 -> Color(0xFF0B6E4F)
    availableLots >= 20 -> Color(0xFFB45309)
    else -> Color(0xFFB42318)
}

private fun Coordinate.toLatLng(): LatLng = LatLng(latitude, longitude)
