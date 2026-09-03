package com.pessoal.agenda.mobile.health.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectOriginFilterTest {
    @Test
    fun productionConfigurationDoesNotRestrictUserSelectedSources() {
        assertTrue(configuredDataOrigins("").isEmpty())
    }

    @Test
    fun fieldTestConfigurationKeepsOnlyExplicitSyntheticPackages() {
        val origins = configuredDataOrigins(
            "androidx.health.connect.client.devtool, androidx.health.connect.client.devtool",
        )

        assertEquals(setOf("androidx.health.connect.client.devtool"), origins.map { it.packageName }.toSet())
    }
}
