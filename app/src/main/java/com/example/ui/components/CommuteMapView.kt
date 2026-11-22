package com.example.ui.components

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.models.JourneyRoute
import com.example.domain.models.MapTileProvider
import com.example.domain.models.TransitStop
import com.example.services.map.MapStyleBuilder
import com.example.ui.theme.ButtonGreyBorder
import com.example.ui.theme.ButtonGreySurface
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LightCanvas
import com.example.ui.theme.ModeBusColor
import com.example.ui.theme.ModeMetroColor
import com.example.ui.theme.ModeRailColor
import com.example.ui.theme.ModeRrtsColor
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val SRC_STOPS = "commute-stops-src"
private const val SRC_TRANSIT_LINES = "commute-transit-lines-src"
private const val SRC_WALK_LINES = "commute-walk-lines-src"
private const val SRC_TRANSFER_PINS = "commute-transfer-pins-src"
private const val SRC_USER_LOCATION = "commute-user-location-src"

private const val LAYER_TRANSIT_GLOW = "commute-transit-glow-layer"
private const val LAYER_TRANSIT_LINES = "commute-transit-lines-layer"
private const val LAYER_WALK_LINES = "commute-walk-lines-layer"
private const val LAYER_STOPS = "commute-stops-layer"
private const val LAYER_TRANSFER_PINS = "commute-transfer-pins-layer"
private const val LAYER_USER_HALO = "commute-user-halo-layer"
private const val LAYER_USER_DOT = "commute-user-dot-layer"

@Composable
fun CommuteMapView(
    tileProvider: MapTileProvider,
    stops: List<TransitStop>,
    selectedStopId: String?,
    activeRoute: JourneyRoute?,
    focusLat: Double,
    focusLon: Double,
    modifier: Modifier = Modifier,
    showZoomControls: Boolean = true,
    showLegend: Boolean = false,
    onStopClicked: ((TransitStop) -> Unit)? = null,
    onCycleTileProvider: (() -> Unit)? = null,
    onRecenterClicked: (() -> Unit)? = null
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var isStyleLoading by remember { mutableStateOf(true) }
    var isUsingOfflineFallback by remember { mutableStateOf(false) }

    val stopsById = remember(stops) { stops.associateBy { it.stopId } }
    val latestStops by rememberUpdatedState(stops)
    val latestStopsById by rememberUpdatedState(stopsById)
    val latestSelectedStopId by rememberUpdatedState(selectedStopId)
    val latestActiveRoute by rememberUpdatedState(activeRoute)
    val latestFocusLat by rememberUpdatedState(focusLat)
    val latestFocusLon by rememberUpdatedState(focusLon)
    val latestOnStopClicked by rememberUpdatedState(onStopClicked)

    Box(modifier = modifier.clip(RoundedCornerShape(22.dp)).background(LightCanvas)) {
        if (isPreview) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("MapLibre Interactive Map (${tileProvider.displayName})", color = TextSecondary)
            }
        } else {
            // Enable opaque TextureView mode so MapLibre renders cleanly inside Compose rounded cards
            // and floating Liquid Glass overlays without SurfaceView z-order punch-through or buffer churn.
            val mapView = remember {
                runCatching { MapLibre.getInstance(context.applicationContext) }
                val options = MapLibreMapOptions.createFromAttributes(context, null).apply {
                    textureMode(true)
                    translucentTextureSurface(false)
                }
                MapView(context, options).apply {
                    onCreate(null)
                }
            }

            // Strictly balanced lifecycle state prevents double onStart/onResume and guarantees
            // MapLibre's GL thread stops before SurfaceTexture releases buffers on navigation/dispose.
            val lifecycleGuard = remember(mapView) { MapLifecycleGuard(mapView) }

            DisposableEffect(lifecycleOwner, mapView) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> lifecycleGuard.start()
                        Lifecycle.Event.ON_RESUME -> lifecycleGuard.resume()
                        Lifecycle.Event.ON_PAUSE -> lifecycleGuard.pause()
                        Lifecycle.Event.ON_STOP -> lifecycleGuard.stop()
                        Lifecycle.Event.ON_DESTROY -> lifecycleGuard.destroy()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    // Pause & stop GL render thread immediately before TextureView detaches
                    lifecycleGuard.pause()
                    lifecycleGuard.stop()
                }
            }

            AndroidView(
                factory = { mapView },
                onRelease = {
                    // Destroy MapView only after AndroidView releases the host view
                    lifecycleGuard.destroy()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("maplibre_map_view"),
                update = { view ->
                    if (mapLibreMap == null) {
                        view.getMapAsync { map ->
                            if (lifecycleGuard.isDestroyed) return@getMapAsync
                            map.uiSettings.apply {
                                isAttributionEnabled = false
                                isLogoEnabled = false
                                isCompassEnabled = true
                                isZoomGesturesEnabled = true
                                isScrollGesturesEnabled = true
                                isRotateGesturesEnabled = true
                                isTiltGesturesEnabled = true
                            }
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(focusLat, focusLon))
                                .zoom(11.4)
                                .build()

                            map.addOnMapClickListener { latLng ->
                                val clickHandler = latestOnStopClicked ?: return@addOnMapClickListener false
                                val screenPoint = map.projection.toScreenLocation(latLng)
                                val hitRect = RectF(
                                    screenPoint.x - 32f,
                                    screenPoint.y - 32f,
                                    screenPoint.x + 32f,
                                    screenPoint.y + 32f
                                )
                                val features = map.queryRenderedFeatures(hitRect, LAYER_STOPS)
                                val tappedId = features.firstOrNull()?.getStringProperty("stopId")
                                val stop = tappedId?.let { latestStopsById[it] }
                                if (stop != null) {
                                    clickHandler(stop)
                                    true
                                } else {
                                    false
                                }
                            }
                            mapLibreMap = map
                        }
                    }
                }
            )

            // Fall back seamlessly to local obsidian canvas if remote style fails (e.g. offline)
            DisposableEffect(mapView, mapLibreMap, tileProvider) {
                var fallbackTriggered = false
                val failListener = MapView.OnDidFailLoadingMapListener {
                    val map = mapLibreMap ?: return@OnDidFailLoadingMapListener
                    if (fallbackTriggered || lifecycleGuard.isDestroyed) return@OnDidFailLoadingMapListener
                    fallbackTriggered = true
                    isUsingOfflineFallback = true
                    applyOfflineFallbackStyleAndLayers(
                        map = map,
                        stops = latestStops,
                        selectedStopId = latestSelectedStopId,
                        activeRoute = latestActiveRoute,
                        userLat = latestFocusLat,
                        userLon = latestFocusLon,
                        onStyleReady = { style ->
                            loadedStyle = style
                            isStyleLoading = false
                        }
                    )
                }
                mapView.addOnDidFailLoadingMapListener(failListener)
                onDispose {
                    mapView.removeOnDidFailLoadingMapListener(failListener)
                }
            }

            // Single-source-of-truth style loading (prevents duplicate async style races)
            LaunchedEffect(mapLibreMap, tileProvider) {
                val map = mapLibreMap ?: return@LaunchedEffect
                isStyleLoading = true
                isUsingOfflineFallback = false
                loadedStyle = null
                applyStyleAndLayers(
                    map = map,
                    provider = tileProvider,
                    stops = latestStops,
                    selectedStopId = latestSelectedStopId,
                    activeRoute = latestActiveRoute,
                    userLat = latestFocusLat,
                    userLon = latestFocusLon,
                    onStyleReady = { style ->
                        loadedStyle = style
                        isStyleLoading = false
                    }
                )
            }

            // Reactive GeoJSON updates whenever stops, selection, active route, or user GPS change
            LaunchedEffect(loadedStyle, stops, selectedStopId, activeRoute, focusLat, focusLon) {
                val style = loadedStyle ?: return@LaunchedEffect
                if (!style.isFullyLoaded) return@LaunchedEffect
                updateGeoJsonSources(style, stops, selectedStopId, activeRoute, focusLat, focusLon)
                val map = mapLibreMap ?: return@LaunchedEffect
                if (activeRoute != null) {
                    fitCameraToRoute(map, activeRoute)
                }
            }
        }

        // Subtle loading indicator while MapLibre initializes style
        AnimatedVisibility(
            visible = isStyleLoading && !isPreview,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xEBFFFFFF))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = PrimaryInk,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Loading MapLibre...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // Optional compact transit mode legend (top-left once loaded)
        if (showLegend && !isStyleLoading) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xEBFFFFFF))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot("Metro", ModeMetroColor)
                LegendDot("Bus", ModeBusColor)
                LegendDot("Rail", ModeRailColor)
                LegendDot("RRTS", ModeRrtsColor)
            }
        }

        // Top-right floating MapLibre controls (Zoom +/-, Fit Route, Tile Switcher, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onCycleTileProvider != null) {
                MapControlIconButton(
                    icon = Icons.Rounded.Layers,
                    description = "Switch map tile provider",
                    tint = PrimaryInk,
                    tag = "map_switch_tile_button",
                    onClick = onCycleTileProvider
                )
            }
            if (onRecenterClicked != null) {
                MapControlIconButton(
                    icon = Icons.Rounded.MyLocation,
                    description = "Recenter on current location",
                    tint = TextPrimary,
                    tag = "map_recenter_button",
                    onClick = {
                        onRecenterClicked()
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(focusLat, focusLon), 12.2)
                        )
                    }
                )
            }
            if (activeRoute != null) {
                MapControlIconButton(
                    icon = Icons.Rounded.CenterFocusStrong,
                    description = "Fit full route on map",
                    tint = PrimaryInk,
                    tag = "map_fit_route_button",
                    onClick = {
                        mapLibreMap?.let { fitCameraToRoute(it, activeRoute) }
                    }
                )
            }
            if (showZoomControls) {
                MapControlIconButton(
                    icon = Icons.Rounded.Add,
                    description = "Zoom in",
                    tint = TextPrimary,
                    tag = "map_zoom_in_button",
                    onClick = { mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
                )
                MapControlIconButton(
                    icon = Icons.Rounded.Remove,
                    description = "Zoom out",
                    tint = TextPrimary,
                    tag = "map_zoom_out_button",
                    onClick = { mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
                )
            }
        }

        // Bottom-left OpenStreetMap attribution pill (mandatory OSM & OpenFreeMap compliance)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xEBFFFFFF))
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isUsingOfflineFallback) {
                    "Offline Transit Canvas · Delhi NCR GTFS"
                } else {
                    "${tileProvider.attribution} · ${tileProvider.displayName}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MapControlIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(ButtonGreySurface.copy(alpha = 0.94f))
            .border(1.dp, ButtonGreyBorder, CircleShape)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

private fun applyStyleAndLayers(
    map: MapLibreMap,
    provider: MapTileProvider,
    stops: List<TransitStop>,
    selectedStopId: String?,
    activeRoute: JourneyRoute?,
    userLat: Double,
    userLon: Double,
    onStyleReady: (Style) -> Unit
) {
    val styleBuilder = if (provider.styleUri != null) {
        Style.Builder().fromUri(provider.styleUri)
    } else {
        Style.Builder().fromJson(MapStyleBuilder.buildStyleJson(provider))
    }

    map.setStyle(styleBuilder) { style ->
        attachTransitLayers(
            style = style,
            stops = stops,
            selectedStopId = selectedStopId,
            activeRoute = activeRoute,
            userLat = userLat,
            userLon = userLon
        )
        onStyleReady(style)
        if (activeRoute != null) {
            fitCameraToRoute(map, activeRoute)
        }
    }
}

private fun applyOfflineFallbackStyleAndLayers(
    map: MapLibreMap,
    stops: List<TransitStop>,
    selectedStopId: String?,
    activeRoute: JourneyRoute?,
    userLat: Double,
    userLon: Double,
    onStyleReady: (Style) -> Unit
) {
    val fallbackBuilder = Style.Builder().fromJson(MapStyleBuilder.buildOfflineFallbackStyleJson())
    map.setStyle(fallbackBuilder) { style ->
        attachTransitLayers(
            style = style,
            stops = stops,
            selectedStopId = selectedStopId,
            activeRoute = activeRoute,
            userLat = userLat,
            userLon = userLon
        )
        onStyleReady(style)
        if (activeRoute != null) {
            fitCameraToRoute(map, activeRoute)
        }
    }
}

private fun attachTransitLayers(
    style: Style,
    stops: List<TransitStop>,
    selectedStopId: String?,
    activeRoute: JourneyRoute?,
    userLat: Double,
    userLon: Double
) {
    runCatching {
        val stopsSrc = GeoJsonSource(SRC_STOPS, MapStyleBuilder.buildStopsGeoJson(stops, selectedStopId))
        val transitSrc = GeoJsonSource(SRC_TRANSIT_LINES, MapStyleBuilder.buildTransitLinesGeoJson(activeRoute))
        val walkSrc = GeoJsonSource(SRC_WALK_LINES, MapStyleBuilder.buildWalkLinesGeoJson(activeRoute))
        val pinsSrc = GeoJsonSource(SRC_TRANSFER_PINS, MapStyleBuilder.buildTransferPointsGeoJson(activeRoute))
        val userSrc = GeoJsonSource(SRC_USER_LOCATION, MapStyleBuilder.buildUserLocationGeoJson(userLat, userLon))

        style.addSource(stopsSrc)
        style.addSource(transitSrc)
        style.addSource(walkSrc)
        style.addSource(pinsSrc)
        style.addSource(userSrc)

        val walkLayer = LineLayer(LAYER_WALK_LINES, SRC_WALK_LINES).withProperties(
            PropertyFactory.lineColor("#64748B"),
            PropertyFactory.lineWidth(3.2f),
            PropertyFactory.lineDasharray(arrayOf(2.0f, 1.8f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
        )
        val transitGlowLayer = LineLayer(LAYER_TRANSIT_GLOW, SRC_TRANSIT_LINES).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(10.0f),
            PropertyFactory.lineOpacity(0.20f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
        val transitLayer = LineLayer(LAYER_TRANSIT_LINES, SRC_TRANSIT_LINES).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(4.8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
        val stopsLayer = CircleLayer(LAYER_STOPS, SRC_STOPS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(
                Expression.switchCase(
                    Expression.eq(Expression.get("selected"), Expression.literal(1)),
                    Expression.literal(9.5f),
                    Expression.literal(5.5f)
                )
            ),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.2f)
        )
        val pinsLayer = CircleLayer(LAYER_TRANSFER_PINS, SRC_TRANSFER_PINS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(8.0f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.4f)
        )
        val userHaloLayer = CircleLayer(LAYER_USER_HALO, SRC_USER_LOCATION).withProperties(
            PropertyFactory.circleColor("#111827"),
            PropertyFactory.circleOpacity(0.16f),
            PropertyFactory.circleRadius(14.0f)
        )
        val userDotLayer = CircleLayer(LAYER_USER_DOT, SRC_USER_LOCATION).withProperties(
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleRadius(5.5f),
            PropertyFactory.circleStrokeColor("#111827"),
            PropertyFactory.circleStrokeWidth(2.6f)
        )

        style.addLayer(walkLayer)
        style.addLayer(transitGlowLayer)
        style.addLayer(transitLayer)
        style.addLayer(stopsLayer)
        style.addLayer(pinsLayer)
        style.addLayer(userHaloLayer)
        style.addLayer(userDotLayer)
    }
}

private fun updateGeoJsonSources(
    style: Style,
    stops: List<TransitStop>,
    selectedStopId: String?,
    activeRoute: JourneyRoute?,
    userLat: Double,
    userLon: Double
) {
    style.getSourceAs<GeoJsonSource>(SRC_STOPS)
        ?.setGeoJson(MapStyleBuilder.buildStopsGeoJson(stops, selectedStopId))
    style.getSourceAs<GeoJsonSource>(SRC_TRANSIT_LINES)
        ?.setGeoJson(MapStyleBuilder.buildTransitLinesGeoJson(activeRoute))
    style.getSourceAs<GeoJsonSource>(SRC_WALK_LINES)
        ?.setGeoJson(MapStyleBuilder.buildWalkLinesGeoJson(activeRoute))
    style.getSourceAs<GeoJsonSource>(SRC_TRANSFER_PINS)
        ?.setGeoJson(MapStyleBuilder.buildTransferPointsGeoJson(activeRoute))
    style.getSourceAs<GeoJsonSource>(SRC_USER_LOCATION)
        ?.setGeoJson(MapStyleBuilder.buildUserLocationGeoJson(userLat, userLon))
}

private fun fitCameraToRoute(map: MapLibreMap, route: JourneyRoute) {
    val points = route.segments.flatMap { it.pathCoordinates }
    if (points.size < 2) return
    val builder = LatLngBounds.Builder()
    points.forEach { pt -> builder.include(LatLng(pt.lat, pt.lon)) }
    runCatching {
        val bounds = builder.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72))
    }
}

private class MapLifecycleGuard(private val mapView: MapView) {
    private var isStarted = false
    private var isResumed = false
    @Volatile
    var isDestroyed = false
        private set

    fun start() {
        if (isDestroyed || isStarted) return
        isStarted = true
        runCatching { mapView.onStart() }
    }

    fun resume() {
        if (isDestroyed || isResumed) return
        if (!isStarted) start()
        isResumed = true
        runCatching { mapView.onResume() }
    }

    fun pause() {
        if (isDestroyed || !isResumed) return
        isResumed = false
        runCatching { mapView.onPause() }
    }

    fun stop() {
        if (isDestroyed || !isStarted) return
        if (isResumed) pause()
        isStarted = false
        runCatching { mapView.onStop() }
    }

    fun destroy() {
        if (isDestroyed) return
        pause()
        stop()
        isDestroyed = true
        runCatching { mapView.onDestroy() }
    }
}

