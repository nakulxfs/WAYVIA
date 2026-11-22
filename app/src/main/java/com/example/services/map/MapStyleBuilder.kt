package com.example.services.map

import com.example.domain.models.JourneyRoute
import com.example.domain.models.MapTileProvider
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitStop
import org.json.JSONArray
import org.json.JSONObject

object MapStyleBuilder {

    fun buildStyleJson(provider: MapTileProvider): String {
        val tilesArray = JSONArray().apply {
            provider.tileUrls.forEach { put(it) }
        }
        val layersArray = JSONArray().apply {
            // Light alabaster background matches the Light Liquid Glass theme while tiles load
            put(
                JSONObject().apply {
                    put("id", "commute-bg")
                    put("type", "background")
                    put("paint", JSONObject().apply {
                        put("background-color", "#F4F6FA")
                    })
                }
            )
            if (tilesArray.length() > 0) {
                put(
                    JSONObject().apply {
                        put("id", "osm-base-layer")
                        put("type", "raster")
                        put("source", "osm-raster")
                        put("minzoom", 0)
                        put("maxzoom", 19)
                    }
                )
            }
        }
        val sourcesObj = JSONObject().apply {
            if (tilesArray.length() > 0) {
                put("osm-raster", JSONObject().apply {
                    put("type", "raster")
                    put("tiles", tilesArray)
                    put("tileSize", 256)
                    put("attribution", provider.attribution)
                })
            }
        }
        return JSONObject().apply {
            put("version", 8)
            put("name", provider.displayName)
            put("sources", sourcesObj)
            put("layers", layersArray)
        }.toString()
    }

    /**
     * Self-contained light alabaster MapLibre style with zero external network dependencies.
     * Used automatically when a remote tile/style server is unreachable (e.g. offline mode).
     */
    fun buildOfflineFallbackStyleJson(): String {
        val layersArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", "commute-offline-bg")
                    put("type", "background")
                    put("paint", JSONObject().apply {
                        put("background-color", "#F4F6FA")
                    })
                }
            )
        }
        return JSONObject().apply {
            put("version", 8)
            put("name", "CommuteBuddy Offline Canvas")
            put("sources", JSONObject())
            put("layers", layersArray)
        }.toString()
    }

    fun buildUserLocationGeoJson(lat: Double, lon: Double): String {
        val feature = JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().put(lon).put(lat))
            })
            put("properties", JSONObject().apply {
                put("role", "user")
            })
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().put(feature))
        }.toString()
    }

    fun buildStopsGeoJson(stops: List<TransitStop>, selectedStopId: String?): String {
        val features = JSONArray()
        for (stop in stops) {
            val color = when (stop.primaryMode) {
                TransitMode.METRO -> "#111827"
                TransitMode.BUS -> "#059669"
                TransitMode.RAILWAY -> "#6D28D9"
                TransitMode.RRTS -> "#D97706"
                TransitMode.WALK -> "#64748B"
            }
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(stop.lon).put(stop.lat))
                    })
                    put("properties", JSONObject().apply {
                        put("stopId", stop.stopId)
                        put("name", stop.name)
                        put("mode", stop.primaryMode.shortLabel)
                        put("color", color)
                        put("selected", if (stop.stopId == selectedStopId) 1 else 0)
                    })
                }
            )
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    fun buildTransitLinesGeoJson(route: JourneyRoute?): String {
        val features = JSONArray()
        route?.segments?.filter { it.mode != TransitMode.WALK }?.forEach { seg ->
            if (seg.pathCoordinates.size >= 2) {
                val coords = JSONArray()
                seg.pathCoordinates.forEach { pt ->
                    coords.put(JSONArray().put(pt.lon).put(pt.lat))
                }
                val color = if (seg.colorHex.startsWith("#")) seg.colorHex else "#${seg.colorHex}"
                features.put(
                    JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", JSONObject().apply {
                            put("type", "LineString")
                            put("coordinates", coords)
                        })
                        put("properties", JSONObject().apply {
                            put("color", color)
                            put("mode", seg.mode.shortLabel)
                            put("route", seg.routeShortName)
                        })
                    }
                )
            }
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    fun buildWalkLinesGeoJson(route: JourneyRoute?): String {
        val features = JSONArray()
        route?.segments?.filter { it.mode == TransitMode.WALK }?.forEach { seg ->
            if (seg.pathCoordinates.size >= 2) {
                val coords = JSONArray()
                seg.pathCoordinates.forEach { pt ->
                    coords.put(JSONArray().put(pt.lon).put(pt.lat))
                }
                features.put(
                    JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", JSONObject().apply {
                            put("type", "LineString")
                            put("coordinates", coords)
                        })
                        put("properties", JSONObject().apply {
                            put("distance", seg.distanceMeters)
                        })
                    }
                )
            }
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    fun buildTransferPointsGeoJson(route: JourneyRoute?): String {
        val features = JSONArray()
        if (route != null) {
            features.put(pointFeature(route.origin.lon, route.origin.lat, "#34D399", "Origin"))
            features.put(pointFeature(route.destination.lon, route.destination.lat, "#F87171", "Destination"))
            val transitLegs = route.segments.filter { it.mode != TransitMode.WALK }
            for (i in 0 until transitLegs.lastIndex) {
                val leg = transitLegs[i]
                features.put(pointFeature(leg.toLon, leg.toLat, "#FBBF24", "Transfer"))
            }
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    private fun pointFeature(lon: Double, lat: Double, color: String, role: String): JSONObject {
        return JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().put(lon).put(lat))
            })
            put("properties", JSONObject().apply {
                put("color", color)
                put("role", role)
            })
        }
    }
}
