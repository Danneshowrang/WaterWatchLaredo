package com.example.waterwatchlaredo

import org.junit.Assert.*
import org.junit.Test
import org.osmdroid.util.GeoPoint

class MapFocusTest {
    @Test fun boundsIncludeEveryReportWithRoomAroundTheEdges() {
        val points = listOf(GeoPoint(27.49, -99.477), GeoPoint(27.492, -99.474), GeoPoint(27.491, -99.476))
        val bounds = reportFocusBounds(points)!!
        for (point in points) {
            assertTrue(point.latitude < bounds.latNorth && point.latitude > bounds.latSouth)
            assertTrue(point.longitude < bounds.lonEast && point.longitude > bounds.lonWest)
        }
    }

    @Test fun colocatedReportsStillHaveUsableBounds() {
        val point = GeoPoint(27.53, -99.48)
        val bounds = reportFocusBounds(listOf(point, point, point))!!
        assertTrue(bounds.latNorth > bounds.latSouth)
        assertTrue(bounds.lonEast > bounds.lonWest)
    }

    @Test fun emptyOrInvalidLocationsCannotMoveTheCamera() {
        assertNull(reportFocusBounds(emptyList()))
        assertNull(reportFocusBounds(listOf(GeoPoint(Double.NaN, -99.48), GeoPoint(91.0, -99.48))))
    }
}
