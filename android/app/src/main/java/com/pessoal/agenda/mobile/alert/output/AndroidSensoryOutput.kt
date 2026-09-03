package com.pessoal.agenda.mobile.alert.output

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.Build
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.data.AlertDeliveryCandidate
import com.pessoal.agenda.mobile.data.AlertDeliveryReason
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class AudioRouteStatus(
    val policy: AudioRoutePolicy,
    val effectiveLabel: String,
    val fallbackReason: String?,
    val headphonesAvailable: Boolean,
    val phoneSpeakerAvailable: Boolean,
)

data class AudioOutputDevice(
    val key: String,
    val label: String,
    val typeLabel: String,
)

data class SensoryOutputResult(
    val deliveredChannels: Set<SensoryChannel>,
    val reason: AlertDeliveryReason? = null,
    val routeStatus: AudioRouteStatus? = null,
)

interface AlertSensoryOutput {
    suspend fun deliver(candidate: AlertDeliveryCandidate): SensoryOutputResult
}

internal class SensoryOutputGate {
    private val active = AtomicBoolean(false)

    fun tryAcquire(): Boolean = active.compareAndSet(false, true)
    fun release() = active.set(false)
}

class AndroidSensoryOutput internal constructor(
    private val context: Context,
    private val outputGate: SensoryOutputGate = PROCESS_OUTPUT_GATE,
    private val preferenceStore: AudioOutputPreferenceStore = AudioOutputPreferenceStore(context),
) : AlertSensoryOutput {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    override suspend fun deliver(candidate: AlertDeliveryCandidate): SensoryOutputResult {
        val requested = candidate.channels - SensoryChannel.VISUAL - SensoryChannel.WEAR_VIBRATION
        if (requested.isEmpty()) return SensoryOutputResult(emptySet())
        if (!outputGate.tryAcquire()) {
            return SensoryOutputResult(emptySet(), AlertDeliveryReason.SENSORY_OVERLAP)
        }
        val delivered = mutableSetOf<SensoryChannel>()
        var reason: AlertDeliveryReason? = null
        var routeStatus: AudioRouteStatus? = null
        try {
            if (SensoryChannel.PHONE_VIBRATION in requested) {
                if (vibrator.hasVibrator()) {
                    vibrateOnce()
                    delivered += SensoryChannel.PHONE_VIBRATION
                    if (SensoryChannel.AUDIO !in requested) delay(VIBRATION_MILLIS)
                } else {
                    reason = AlertDeliveryReason.ROUTE_UNAVAILABLE
                }
            }
            if (SensoryChannel.AUDIO in requested) {
                val result = playTone(resolveRoute(candidate.audioRoute, preferenceStore.selectedDeviceKey()))
                routeStatus = result.routeStatus
                if (result.played) delivered += SensoryChannel.AUDIO
                if (result.reason != null) reason = result.reason
            }
            if (SensoryChannel.WEAR_VIBRATION in candidate.channels) {
                reason = AlertDeliveryReason.ROUTE_UNAVAILABLE
            }
            return SensoryOutputResult(delivered, reason, routeStatus)
        } finally {
            outputGate.release()
        }
    }

    fun routeStatus(
        policy: AudioRoutePolicy,
        preferredDeviceKey: String? = preferenceStore.selectedDeviceKey(),
    ): AudioRouteStatus = resolveRoute(policy, preferredDeviceKey).status

    fun availableHeadphoneDevices(): List<AudioOutputDevice> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in HEADPHONE_TYPES }
            .map(::descriptor)
            .distinctBy(AudioOutputDevice::key)
            .sortedBy { it.label.lowercase() }

    private fun vibrateOnce() {
        val effect = VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AUDIO_ATTRIBUTES)
        }
    }

    suspend fun testTone(
        policy: AudioRoutePolicy,
        preferredDeviceKey: String? = preferenceStore.selectedDeviceKey(),
    ): SensoryOutputResult {
        val route = resolveRoute(policy, preferredDeviceKey)
        if (route.deviceRequiredButUnavailable) {
            return SensoryOutputResult(emptySet(), AlertDeliveryReason.ROUTE_UNAVAILABLE, route.status)
        }
        if (!outputGate.tryAcquire()) {
            return SensoryOutputResult(emptySet(), AlertDeliveryReason.SENSORY_OVERLAP, route.status)
        }
        try {
            val result = playTone(route)
            return SensoryOutputResult(
                deliveredChannels = if (result.played) setOf(SensoryChannel.AUDIO) else emptySet(),
                reason = result.reason,
                routeStatus = result.routeStatus,
            )
        } finally {
            outputGate.release()
        }
    }

    private suspend fun playTone(route: ResolvedRoute): ToneResult {
        if (route.deviceRequiredButUnavailable) {
            return ToneResult(false, AlertDeliveryReason.ROUTE_UNAVAILABLE, route.status)
        }
        if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            return ToneResult(false, AlertDeliveryReason.SYSTEM_POLICY, route.status)
        }
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener { }
            .build()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return ToneResult(false, AlertDeliveryReason.SYSTEM_POLICY, route.status)
        }
        var track: AudioTrack? = null
        return try {
            val samples = toneSamples()
            track = AudioTrack.Builder()
                .setAudioAttributes(AUDIO_ATTRIBUTES)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .build()
            val preferredAccepted = route.preferredDevice?.let(track::setPreferredDevice) ?: true
            check(track.write(samples, 0, samples.size) == samples.size) { "Falha ao preparar teste de áudio." }
            track.setVolume(TONE_VOLUME)
            track.play()
            delay(TONE_MILLIS + 100L)
            val fallback = route.status.fallbackReason != null || !preferredAccepted
            ToneResult(
                played = true,
                reason = if (fallback) AlertDeliveryReason.AUDIO_FALLBACK else null,
                routeStatus = if (preferredAccepted) route.status else route.status.copy(
                    effectiveLabel = "Rota automática do sistema",
                    fallbackReason = "O Android recusou a rota preferida; foi usada a rota do sistema.",
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ToneResult(false, AlertDeliveryReason.SYSTEM_FAILURE, route.status)
        } finally {
            runCatching { track?.stop() }
            track?.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun resolveRoute(policy: AudioRoutePolicy, preferredDeviceKey: String?): ResolvedRoute {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val headphones = devices.filter { it.type in HEADPHONE_TYPES }
        val selectedHeadphone = preferredDeviceKey?.let { key ->
            headphones.firstOrNull { deviceKey(it) == key }
        }
        val preferredHeadphone = if (preferredDeviceKey == null) headphones.firstOrNull() else selectedHeadphone
        val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val status = when (policy) {
            AudioRoutePolicy.SYSTEM_DEFAULT -> AudioRouteStatus(
                policy, "Rota automática do sistema", null, headphones.isNotEmpty(), speaker != null,
            )
            AudioRoutePolicy.PREFER_HEADPHONES -> if (preferredHeadphone != null) {
                AudioRouteStatus(policy, preferredHeadphone.productName.toString(), null, true, speaker != null)
            } else {
                AudioRouteStatus(
                    policy,
                    speaker?.productName?.toString() ?: "Rota automática do sistema",
                    if (preferredDeviceKey == null) {
                        "Fone indisponível; o áudio usará o telefone ou a rota do sistema."
                    } else {
                        "Dispositivo escolhido indisponível; o áudio usará o telefone ou a rota do sistema."
                    },
                    headphones.isNotEmpty(),
                    speaker != null,
                )
            }
            AudioRoutePolicy.PREFER_PHONE -> if (speaker != null) {
                AudioRouteStatus(policy, speaker.productName.toString(), null, headphones.isNotEmpty(), true)
            } else {
                AudioRouteStatus(
                    policy,
                    "Rota automática do sistema",
                    "Alto-falante não identificado; o Android escolherá a saída.",
                    headphones.isNotEmpty(),
                    false,
                )
            }
            AudioRoutePolicy.VIBRATION_ONLY -> AudioRouteStatus(
                policy, "Som desativado; somente vibração", null, headphones.isNotEmpty(), speaker != null,
            )
            AudioRoutePolicy.NONE -> AudioRouteStatus(
                policy, "Sem saída sonora", null, headphones.isNotEmpty(), speaker != null,
            )
        }
        val preferred = when (policy) {
            AudioRoutePolicy.PREFER_HEADPHONES -> preferredHeadphone ?: speaker
            AudioRoutePolicy.PREFER_PHONE -> speaker
            else -> null
        }
        return ResolvedRoute(
            preferredDevice = preferred,
            status = status,
            deviceRequiredButUnavailable = policy in setOf(AudioRoutePolicy.VIBRATION_ONLY, AudioRoutePolicy.NONE),
        )
    }

    private fun descriptor(device: AudioDeviceInfo) = AudioOutputDevice(
        key = deviceKey(device),
        label = device.productName.toString().ifBlank { "Saída de áudio" },
        typeLabel = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Com fio"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            else -> "Áudio externo"
        },
    )

    private fun deviceKey(device: AudioDeviceInfo): String =
        "${device.type}:${device.productName.toString().trim()}"

    private fun toneSamples(): ShortArray {
        val count = SAMPLE_RATE * TONE_MILLIS / 1_000
        return ShortArray(count) { index ->
            val envelope = minOf(1.0, index / 500.0, (count - index) / 500.0).coerceAtLeast(0.0)
            (sin(2.0 * PI * TONE_FREQUENCY * index / SAMPLE_RATE) * Short.MAX_VALUE * envelope).toInt().toShort()
        }
    }

    private data class ResolvedRoute(
        val preferredDevice: AudioDeviceInfo?,
        val status: AudioRouteStatus,
        val deviceRequiredButUnavailable: Boolean,
    )

    private data class ToneResult(
        val played: Boolean,
        val reason: AlertDeliveryReason?,
        val routeStatus: AudioRouteStatus,
    )

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val TONE_MILLIS = 700
        const val VIBRATION_MILLIS = 120L
        const val TONE_FREQUENCY = 660.0
        const val TONE_VOLUME = 0.35f
        val PROCESS_OUTPUT_GATE = SensoryOutputGate()
        val AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
