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
}
