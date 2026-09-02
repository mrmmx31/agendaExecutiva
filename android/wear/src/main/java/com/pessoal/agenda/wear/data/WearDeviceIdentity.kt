package com.pessoal.agenda.wear.data

import android.content.Context
import java.util.UUID

class WearDeviceIdentity(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val deviceId: String
        get() = preferences.getString(DEVICE_ID, null)?.also(UUID::fromString)
            ?: UUID.randomUUID().toString().also {
                check(preferences.edit().putString(DEVICE_ID, it).commit())
            }

    private companion object {
        const val PREFERENCES = "agenda_wear_identity"
        const val DEVICE_ID = "device_id"
    }
}
