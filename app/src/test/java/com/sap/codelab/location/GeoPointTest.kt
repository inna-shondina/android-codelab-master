package com.sap.codelab.location

import org.junit.Assert.assertEquals
import org.junit.Test

internal class GeoPointTest {

    @Test
    fun `boundary coordinates are valid`() {
        assertEquals(90.0, GeoPoint(90.0, 180.0).latitude, 0.0)
        assertEquals(-180.0, GeoPoint(-90.0, -180.0).longitude, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude outside world bounds is rejected`() {
        GeoPoint(90.0001, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longitude outside world bounds is rejected`() {
        GeoPoint(0.0, -180.0001)
    }

    @Test
    fun `distance uses great circle calculation`() {
        val sofia = GeoPoint(42.6977, 23.3219)
        val pointAboutOneHundredMetresNorth = GeoPoint(42.6986, 23.3219)

        assertEquals(100.0, sofia.distanceTo(pointAboutOneHundredMetresNorth), 1.0)
    }
}
