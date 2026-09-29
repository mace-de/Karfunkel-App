package de.oliverpekel.karfunkel.ui

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.oliverpekel.karfunkel.FocusRequest
import de.oliverpekel.karfunkel.MapGroup
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.accumulated
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gt
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.sum
import org.maplibre.android.style.expressions.Expression.toString as exprToString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Karte mit OpenFreeMap-Vektorkacheln (Stil "dark", Hintergrund auf reines Schwarz gesetzt).
 * Termine am selben Ort bilden einen Marker; nahe Marker werden je nach Zoomstufe zusammengefasst.
 */
@Composable
fun EventMap(
    groups: List<MapGroup>,
    focus: FocusRequest?,
    visible: Boolean,
    onGroupClick: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = remember { MapController(context) }
    controller.onGroupClick = onGroupClick

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> controller.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.mapView.onPause()
                Lifecycle.Event.ON_STOP -> controller.mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }

    LaunchedEffect(groups) { controller.setGroups(groups) }
    LaunchedEffect(focus) { focus?.let { controller.focus(it) } }

    AndroidView(
        factory = { controller.mapView },
        update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
        modifier = modifier,
    )
}

private class MapController(context: Context) {

    var onGroupClick: (List<Int>) -> Unit = {}

    private val density = context.resources.displayMetrics.density
    private var map: MapLibreMap? = null
    private var source: GeoJsonSource? = null
    private var groups: List<MapGroup> = emptyList()
    private var pendingFocus: FocusRequest? = null
    private var destroyed = false
    private var cameraInitialized = false

    val mapView: MapView = MapView(
        context,
        MapLibreMapOptions.createFromAttributes(context)
            .camera(CameraPosition.Builder().target(LatLng(51.2, 10.4)).zoom(4.7).build())
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .compassEnabled(false)
            .logoGravity(Gravity.BOTTOM or Gravity.START)
            .attributionGravity(Gravity.BOTTOM or Gravity.START)
            .attributionMargins(intArrayOf((92 * density).toInt(), 0, 0, (4 * density).toInt()))
            .attributionTintColor(GoldDim.toArgb())
            .foregroundLoadColor(Black.toArgb()),
    ).apply {
        setBackgroundColor(Black.toArgb())
        onCreate(null)
        getMapAsync { m ->
            map = m
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style -> setupStyle(style) }
            m.addOnMapClickListener { handleClick(it) }
        }
    }

    fun setGroups(list: List<MapGroup>) {
        groups = list
        source?.setGeoJson(toFeatures(list))
    }

    fun focus(request: FocusRequest) {
        val m = map
        if (m == null || source == null) {
            pendingFocus = request
            return
        }
        m.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(request.pos.lat, request.pos.lon), 12.0),
            700,
        )
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        mapView.onStop()
        mapView.onDestroy()
    }

    private fun setupStyle(style: Style) {
        style.getLayer("background")?.setProperties(backgroundColor(Black.toArgb()))
        // Ortsnamen auf Deutsch statt Englisch (OpenMapTiles liefert "name:de")
        style.layers.filterIsInstance<SymbolLayer>()
            .filter { it.textField.expression?.toString()?.contains("name_en") == true }
            .forEach { it.setProperties(textField(coalesce(get("name:de"), get("name")))) }

        val src = GeoJsonSource(
            SOURCE_ID,
            toFeatures(groups),
            GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(46)
                .withClusterMaxZoom(13)
                .withClusterProperty("total", sum(accumulated(), get("total")), get("count")),
        )
        style.addSource(src)
        source = src

        val gold = Gold.toArgb()
        style.addLayer(
            CircleLayer(CLUSTER_LAYER, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    circleColor(KarfunkelDark.toArgb()),
                    circleRadius(step(get("total"), literal(14f), stop(10, 18f), stop(40, 23f))),
                    circleStrokeColor(gold),
                    circleStrokeWidth(1.5f),
                ),
        )
        style.addLayer(
            SymbolLayer("$CLUSTER_LAYER-count", SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    textField(exprToString(get("total"))),
                    textFont(arrayOf("Noto Sans Bold")),
                    textSize(12f),
                    textColor(TextPrimary.toArgb()),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
        style.addLayer(
            CircleLayer(POINT_LAYER, SOURCE_ID)
                .withFilter(not(has("point_count")))
                .withProperties(
                    circleColor(Karfunkel.toArgb()),
                    circleRadius(step(get("count"), literal(7f), stop(2, 10f))),
                    circleStrokeColor(gold),
                    circleStrokeWidth(2f),
                ),
        )
        style.addLayer(
            SymbolLayer("$POINT_LAYER-count", SOURCE_ID)
                .withFilter(all(not(has("point_count")), gt(get("count"), literal(1))))
                .withProperties(
                    textField(exprToString(get("count"))),
                    textFont(arrayOf("Noto Sans Bold")),
                    textSize(11f),
                    textColor(TextPrimary.toArgb()),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
        val pending = pendingFocus
        if (pending != null) {
            pendingFocus = null
            focus(pending)
        } else if (!cameraInitialized) {
            // Erst nach dem Layout ist die Kartengröße bekannt, die für newLatLngBounds nötig ist.
            mapView.post {
                map?.moveCamera(CameraUpdateFactory.newLatLngBounds(GERMANY, (12 * density).toInt()))
            }
        }
        cameraInitialized = true
    }

    private fun handleClick(point: LatLng): Boolean {
        val m = map ?: return false
        val src = source ?: return false
        val p = m.projection.toScreenLocation(point)
        val r = 16 * density
        val rect = RectF(p.x - r, p.y - r, p.x + r, p.y + r)

        m.queryRenderedFeatures(rect, CLUSTER_LAYER).firstOrNull()?.let { cluster ->
            val center = cluster.geometry() as Point
            val zoom = src.getClusterExpansionZoom(cluster).toDouble()
            m.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(center.latitude(), center.longitude()), zoom + 0.3),
                500,
            )
            return true
        }
        m.queryRenderedFeatures(rect, POINT_LAYER).firstOrNull()?.let { feature ->
            val ids = feature.getStringProperty("ids").split(',').mapNotNull { it.toIntOrNull() }
            if (ids.isNotEmpty()) onGroupClick(ids)
            return true
        }
        return false
    }

    private fun toFeatures(list: List<MapGroup>): FeatureCollection =
        FeatureCollection.fromFeatures(list.map { g ->
            Feature.fromGeometry(Point.fromLngLat(g.pos.lon, g.pos.lat)).apply {
                addStringProperty("ids", g.eventIds.joinToString(","))
                addNumberProperty("count", g.eventIds.size)
            }
        })

    companion object {
        val GERMANY: LatLngBounds = LatLngBounds.from(55.06, 15.04, 47.27, 5.87)
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
        const val SOURCE_ID = "events"
        const val CLUSTER_LAYER = "events-cluster"
        const val POINT_LAYER = "events-point"
    }
}
