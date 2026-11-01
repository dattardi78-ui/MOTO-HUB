// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.feature.controls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Phone-side verbs for riding WITHOUT Android Auto: the TFT mirrors Google Maps, and the
 * handlebar remote (e.g. a Bluetooth HID remote such as a LIVALL BR80) drives the phone itself.
 *
 * None of these go through the Android Auto input path, so they work in plain mirroring mode.
 */
object PhoneActions {

    /** Call state read from the audio mode: needs no phone permission at all. */
    enum class CallState { IDLE, RINGING, IN_CALL }

    fun callState(context: Context): CallState {
        val audio = context.getSystemService(AudioManager::class.java) ?: return CallState.IDLE
        return when (audio.mode) {
            AudioManager.MODE_RINGTONE -> CallState.RINGING
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION -> CallState.IN_CALL
            else -> CallState.IDLE
        }
    }

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED

    /** Opens the phone's default voice assistant (Gemini, when it is set as default). */
    fun openVoiceAssistant(context: Context, log: (String) -> Unit): Boolean {
        val intents = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent(Intent.ACTION_ASSIST),
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                log("[PHONE] voice assistant opened (${intent.action})")
                return true
            } catch (_: Exception) { }
        }
        log("[PHONE] no voice assistant could be opened")
        return false
    }

    /** Answers a ringing call; otherwise opens the voice assistant. One button, two jobs. */
    fun answerOrVoice(context: Context, log: (String) -> Unit): Boolean =
        if (callState(context) == CallState.RINGING) answer(context, log)
        else openVoiceAssistant(context, log)

    @SuppressLint("MissingPermission")
    fun answer(context: Context, log: (String) -> Unit): Boolean {
        if (!hasCallPermission(context)) {
            log("[PHONE] cannot answer: grant 'Answer phone calls' to MOTO-HUB")
            return false
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            telecom.acceptRingingCall()
            log("[PHONE] call answered")
            true
        }.getOrElse {
            log("[PHONE] answer failed: ${it.message}")
            false
        }
    }

    /** Ends the active call, or rejects a ringing one. */
    @SuppressLint("MissingPermission")
    fun hangUp(context: Context, log: (String) -> Unit): Boolean {
        if (callState(context) == CallState.IDLE) {
            log("[PHONE] no call to end")
            return false
        }
        if (!hasCallPermission(context)) {
            log("[PHONE] cannot hang up: grant 'Answer phone calls' to MOTO-HUB")
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            val ended = telecom.endCall()
            log("[PHONE] end call -> $ended")
            ended
        }.getOrElse {
            log("[PHONE] hang up failed: ${it.message}")
            false
        }
    }

    /** Brings Google Maps to the front, so single-app mirroring shows the map again. */
    fun openMaps(context: Context, log: (String) -> Unit): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE)
        if (intent == null) {
            log("[PHONE] Google Maps is not installed")
            return false
        }
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
            log("[PHONE] Google Maps brought to front")
            true
        }.getOrElse {
            log("[PHONE] opening Maps failed: ${it.message}")
            false
        }
    }

    private const val MAPS_PACKAGE = "com.google.android.apps.maps"
}
