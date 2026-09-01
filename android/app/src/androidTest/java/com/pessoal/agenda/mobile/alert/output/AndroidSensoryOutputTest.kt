package com.pessoal.agenda.mobile.alert.output

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSensoryOutputTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val output = AndroidSensoryOutput(context)

    @Test
    fun systemRouteReportsAnEffectiveOutputWithoutPlayingAudio() {
        val status = output.routeStatus(AudioRoutePolicy.SYSTEM_DEFAULT)

        assertEquals(AudioRoutePolicy.SYSTEM_DEFAULT, status.policy)
        assertNotNull(status.effectiveLabel.takeIf(String::isNotBlank))
    }

    @Test
    fun unavailableHeadphonesExposeVisibleFallback() {
        val preferred = output.routeStatus(AudioRoutePolicy.PREFER_HEADPHONES)
        val automatic = output.routeStatus(AudioRoutePolicy.SYSTEM_DEFAULT)

        assertEquals(AudioRoutePolicy.PREFER_HEADPHONES, preferred.policy)
        if (!preferred.headphonesAvailable) assertNotNull(preferred.fallbackReason)
        assertEquals(AudioRoutePolicy.SYSTEM_DEFAULT, automatic.policy)
        assertEquals(null, automatic.fallbackReason)
    }

    @Test
    fun vibrationOnlyNeverReportsAnAudioFallback() {
        val status = output.routeStatus(AudioRoutePolicy.VIBRATION_ONLY)

        assertEquals("Som desativado; somente vibração", status.effectiveLabel)
        assertEquals(null, status.fallbackReason)
    }
}
