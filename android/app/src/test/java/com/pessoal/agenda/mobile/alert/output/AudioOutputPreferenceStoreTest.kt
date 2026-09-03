package com.pessoal.agenda.mobile.alert.output

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioOutputPreferenceStoreTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("agenda_audio_output", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun persistsAndClearsSelectedDeviceWithoutBluetoothAddress() {
        val store = AudioOutputPreferenceStore(context)

        store.saveSelectedDeviceKey("8:MOTO XT220")
        assertEquals("8:MOTO XT220", AudioOutputPreferenceStore(context).selectedDeviceKey())

        store.saveSelectedDeviceKey(null)
        assertNull(store.selectedDeviceKey())
    }
}
