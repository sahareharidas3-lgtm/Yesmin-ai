package com.yesmin.ai.device

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.yesmin.ai.assistant.AppState
import com.yesmin.ai.service.ReminderReceiver
import kotlin.math.roundToInt

/**
 * Real phone actions through public Android APIs/intents only. Every function returns an honest [Outcome].
 * No accessibility hacks, no root, no security bypass.
 */
class DeviceController(private val app: Application) {
    data class Outcome(val ok: Boolean, val message: String)
    sealed class Contact {
        data class Found(val name: String, val number: String) : Contact()
        object NoPermission : Contact()
        object NotFound : Contact()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    /**
     * Android 10+ blocks apps from starting other screens from the background. If YESMIN isn't visible
     * we post a "tap to open" notification instead and report that honestly.
     */
    private fun launch(intent: Intent, okMsg: String, fallback: Intent? = null): Outcome {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallback?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!AppState.foreground) {
            val posted = Notifications.postAction(app, okMsg, intent)
            return Outcome(false, if (posted)
                "Android background se apps nahi kholne deta, isliye maine notification bheji hai — usse tap kijiye."
            else
                "Android background se apps nahi kholne deta, aur notification permission bhi nahi hai. YESMIN ko khol kar phir boliye.")
        }
        return try {
            app.startActivity(intent); Outcome(true, okMsg)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) try { app.startActivity(fallback); Outcome(true, okMsg) }
            catch (e2: Exception) { Outcome(false, "Is phone me iske liye koi app nahi mila.") }
            else Outcome(false, "Is phone me iske liye koi app nahi mila.")
        } catch (e: SecurityException) {
            Outcome(false, "Android ne permission nahi di.")
        } catch (e: Exception) {
            Outcome(false, "Open nahi ho paaya: ${e.message}")
        }
    }

    // ---------- apps ----------
    private val known = mapOf(
        "youtube" to "com.google.android.youtube", "whatsapp" to "com.whatsapp", "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android", "facebook" to "com.facebook.katana", "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music", "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps", "play store" to "com.android.vending",
        "phonepe" to "com.phonepe.app", "paytm" to "net.one97.paytm", "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user", "youtube music" to "com.google.android.apps.youtube.music"
    )

    fun openApp(name: String): Outcome {
        val n = name.lowercase().trim()
        if (n.isEmpty()) return Outcome(false, "Kaunsa app kholna hai?")
        val pm = app.packageManager
        var label = name
        var intent = known[n]?.let { pm.getLaunchIntentForPackage(it) }
        if (intent == null) {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val apps = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
                       else pm.queryIntentActivities(main, 0)
            val named = apps.map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            val hit = named.firstOrNull { it.first.equals(n, true) } ?: named.firstOrNull { it.first.startsWith(n, true) }
                ?: named.firstOrNull { it.first.contains(n, true) }
            if (hit != null) { label = hit.first; intent = pm.getLaunchIntentForPackage(hit.second) }
        } else label = name.replaceFirstChar { it.uppercase() }
        if (intent == null) return Outcome(false, "'$name' naam ka app is phone me nahi mila.")
        return launch(intent, "$label khol diya")
    }

    fun openCamera() = launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera khol diya")

    fun openGallery(): Outcome = launch(
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY), "Gallery khol di",
        fallback = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    )

    fun openSettingsPage(page: String): Outcome {
        val (action, label) = when (page) {
            "wifi" -> (if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS) to "Wi-Fi / internet panel"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth settings"
            "display" -> Settings.ACTION_DISPLAY_SETTINGS to "Display settings"
            "sound" -> Settings.ACTION_SOUND_SETTINGS to "Sound settings"
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS to "Battery settings"
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location settings"
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS to "Airplane mode settings"
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS to "Storage settings"
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS to "Accessibility settings"
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS to "Apps settings"
            else -> Settings.ACTION_SETTINGS to "Settings"
        }
        return launch(Intent(action), "$label khol diya")
    }

    // ---------- hardware ----------
    fun setTorch(on: Boolean): Outcome {
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Outcome(false, "Is phone me flashlight nahi mili.")
            cm.setTorchMode(id, on)
            Outcome(true, if (on) "Flashlight chalu kar di" else "Flashlight band kar di")
        } catch (e: CameraAccessException) {
            Outcome(false, "Camera abhi kisi aur app ke paas hai, torch nahi chal paayi.")
        } catch (e: Exception) {
            Outcome(false, "Torch control nahi ho paaya: ${e.message}")
        }
    }

    fun adjustVolume(mode: String, percent: Int?): Outcome {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = maxOf(1, (max / 7f).roundToInt())
        val target = when (mode) {
            "up" -> (cur + step).coerceAtMost(max)
            "down" -> (cur - step).coerceAtLeast(0)
            "max" -> max
            "mute" -> 0
            "set" -> ((percent ?: 50) * max / 100f).roundToInt().coerceIn(0, max)
            else -> cur
        }
        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (now == target) Outcome(true, "Media volume ab ${now * 100 / max}% hai")
            else Outcome(false, "Volume badal nahi paayi (abhi ${now * 100 / max}% hai)")
        } catch (e: SecurityException) { Outcome(false, "Volume change karne ki permission nahi mili.") }
    }

    // ---------- calls (always confirmed by the engine first) ----------
    fun lookupContact(query: String): Contact {
        if (!granted(Manifest.permission.READ_CONTACTS)) return Contact.NoPermission
        val c = app.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        ) ?: return Contact.NotFound
        c.use { if (it.moveToFirst()) return Contact.Found(it.getString(0), it.getString(1)) }
        return Contact.NotFound
    }

    fun placeCall(number: String): Outcome {
        val uri = Uri.fromParts("tel", number, null)
        return if (granted(Manifest.permission.CALL_PHONE)) launch(Intent(Intent.ACTION_CALL, uri), "$number par call laga rahi hoon")
        else launch(Intent(Intent.ACTION_DIAL, uri), "Dialer khol diya — call button aap dabaiye (direct call ke liye Phone permission dein)")
    }

    // ---------- music ----------
    fun playMusic(query: String): Outcome {
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
        return launch(yt, "YouTube par '$query' ke results khol diye (song aap tap karke play kijiye — Android auto-play force nahi karne deta)", web)
    }

    // ---------- reminders ----------
    fun scheduleReminder(minutes: Int, text: String): Outcome {
        val am = app.getSystemService(AlarmManager::class.java)
        val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val pi = PendingIntent.getBroadcast(
            app, id, Intent(app, ReminderReceiver::class.java).putExtra("text", text).putExtra("nid", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Inexact-but-doze-friendly alarm: needs no special permission; may fire a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pi)
        val warn = if (!Notifications.enabled(app)) " (Notification permission off hai, isliye alert dikhega nahi — permission on kijiye)" else ""
        return Outcome(true, "$minutes minute baad yaad dilaungi: $text$warn")
    }
}
