package com.pessoal.agenda.mobile.alert.output

import android.content.Context

class AudioOutputPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selectedDeviceKey(): String? = preferences.getString(SELECTED_DEVICE, null)

    fun saveSelectedDeviceKey(deviceKey: String?) {
        preferences.edit().apply {
            if (deviceKey == null) remove(SELECTED_DEVICE) else putString(SELECTED_DEVICE, deviceKey)
        }.apply()
    }

    private companion object {
        const val PREFERENCES = "agenda_audio_output"
        const val SELECTED_DEVICE = "selected_device"
    }
}
