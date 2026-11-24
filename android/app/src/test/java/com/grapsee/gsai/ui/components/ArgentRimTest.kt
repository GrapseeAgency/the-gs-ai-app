package com.grapsee.gsai.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentRimTest {
    @Test fun onlyComponentSizedSurfacesReceiveTheRim() {
        assertTrue(argentComponentSize(40f,40f))
        assertTrue(argentComponentSize(360f,96f))
        assertFalse(argentComponentSize(360f,780f))
        assertFalse(argentComponentSize(780f,360f))
        assertFalse(argentComponentSize(5f,5f))
    }

    @Test fun directionalRimHasMutedFixedCornerHighlights() {
        val dark = floatArrayOf(.07f,.38f)
        val topLeft = ArgentRimSample.alpha(0.0,0.0,dark)
        val topRight = ArgentRimSample.alpha(1.0,0.0,dark)
        val bottomRight = ArgentRimSample.alpha(1.0,1.0,dark)
        assertTrue(topLeft > topRight + 50)
        assertTrue(topLeft == bottomRight)
        assertTrue(topLeft <= 97)
    }

    @Test fun upstreamFieldHasSpatialReflectionBandsAndTimeEvolution() {
        val light = floatArrayOf(248/255f,250/255f,252/255f)
        val dark = floatArrayOf(35/255f,38/255f,43/255f)
        fun sample(t: Double) = (0..120 step 4).map {
            ArgentRimSample.color(it.toDouble(),0.5,120.0,48.0,t,light,dark,.015)
                .joinToString(",")
        }
        val initial = sample(3.4)
        assertTrue(initial.toSet().size > 8)
        assertTrue(initial != sample(4.4))
    }
}
