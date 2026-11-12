# YESMIN AI – Your Cute Romantic AI Companion (Android, Kotlin, Compose)

A native Android app: Gemini-powered chat brain, Hindi/Hinglish/English voice (speech-to-text + text-to-speech),
wake word "Yesmin", real phone control (apps, torch, volume, calls with confirmation, music, notes, reminders),
and an animated avatar. Kotlin · Jetpack Compose · Material 3 · MVVM · Coroutines.

> **Status:** written to be a complete, working project, but it was authored without access to an Android SDK,
> so the very first Gradle sync/build on your PC is its first compile. If Android Studio shows a red error, copy it
> back to Claude and it can be fixed quickly. See "Troubleshooting".

---
## 1. Setup (beginner-friendly)

1. Install **Android Studio** (Koala 2024.1 or newer) from developer.android.com/studio. It bundles JDK 17.
2. Unzip `YesminAI.zip`. In Android Studio: **File → Open →** select the `YesminAI` folder. Wait for "Gradle sync" to finish
   (first time downloads ~1 GB; needs internet). Accept any "install missing SDK platform 35" prompts.
3. **Get a Gemini API key** (free tier available): https://aistudio.google.com/apikey
4. **Run on your Vivo T4x:**
   - Phone: Settings → About phone → tap *Software version/Build number* 7 times → Developer options → enable **USB debugging**.
   - Plug in via USB, accept the "Allow debugging" prompt, pick the phone at the top of Android Studio, press ▶ Run.
5. In the app: ⚙️ Settings → **Gemini API** → paste key → *Save key* → *Fetch available models* → *Test connection*.
6. Tap the mic once → accept the consent dialog → allow microphone. Say "open YouTube" or "kaise ho?".

## 2. Build an installable APK

**Debug APK (easiest, for your own phone):**
Android Studio → **Build → Build Bundle(s) / APK(s) → Build APK(s)** → click "locate".
File: `app/build/outputs/apk/debug/app-debug.apk`. Copy to the phone, open it, allow "Install unknown apps" for your file manager.

**Command line:** the wrapper *jar* can't be shipped in a zip of text files, so run once: `gradle wrapper --gradle-version 8.9`
(needs Gradle installed), then `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). Android Studio users can ignore this.

**Release APK (to share):** Build → *Generate Signed Bundle / APK* → APK → create a keystore (keep it and its password safe; never commit it)
→ choose *release*. Output: `app/build/outputs/apk/release/`.

## 3. Project structure
```
app/src/main/java/com/yesmin/ai/
├─ YesminApp.kt, Graph.kt            app class + simple dependency container
├─ data/    Models, SettingsRepository, SecureStore (Keystore AES-GCM), PersistentList (JSON files)
├─ ai/      GeminiClient (REST, model list, test, error log), Persona (system prompt)
├─ assistant/ AssistantEngine (brain), CommandRouter (phone commands), Calculator, WakeWord, AppState
├─ voice/   TtsManager (+lip-sync kicks), SttManager (SpeechRecognizer), Speech (text cleaner)
├─ device/  DeviceController (apps, torch, volume, calls, music, reminders), Notifications
├─ service/ WakeWordService (foreground, microphone), ReminderReceiver
└─ ui/      MainActivity, MainViewModel, theme/, avatar/YesminAvatar, screens/ (Main, Settings×10, Notes)
```
MVVM: `MainViewModel` exposes StateFlows from the shared `Graph`/`AssistantEngine`; Compose screens only render state.

## 4. Feature → how it really works → what it needs
| Feature | Implementation | Needs |
|---|---|---|
| AI brain | Gemini REST `generateContent`, persona prompt, last 16 messages, optional Google Search grounding | Internet + **your Gemini API key** (external service) |
| Speech-to-text | `SpeechRecognizer` (hi-IN / en-IN) | Mic permission; Google speech service (online unless offline pack + "Prefer offline") |
| Text-to-speech | `TextToSpeech`, selectable voice, pitch/speed, per-word lip-sync | A TTS engine (Google TTS preinstalled on Vivo); Hindi/English voice data |
| Wake word | Foreground service looping `SpeechRecognizer`, matches "yesmin"/variants | Mic + notification permission; see limits below |
| Open apps / settings / camera / gallery | Launcher lookup by name, official intents | none |
| Torch | `CameraManager.setTorchMode` | none (fails honestly if camera busy) |
| Volume | `AudioManager` media stream, verified after change | none |
| Calls | Always asks "Haan/Nahi" (voice or dialog) first; `ACTION_CALL`, else dialer | CALL_PHONE (optional), READ_CONTACTS (for names) |
| Music / YouTube | `ACTION_SEARCH` to YouTube app (browser fallback) | YouTube app |
| Notes / memory | Local JSON in app-private storage | none |
| Reminders | `AlarmManager.setAndAllowWhileIdle` + notification | Notifications permission |
| Calculator | Own expression parser (offline) | none |
| Research / translate / code / writing | Gemini (+ Search grounding toggle) | Gemini key |

## 5. Permissions explained
- **RECORD_AUDIO** – listen when you press the mic or enable wake word. Requested only after the consent dialog.
- **FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE** – required by Android to listen in background; shows a permanent notification with a Stop button.
- **POST_NOTIFICATIONS** – that notification, reminders, and "tap to open" fallbacks.
- **CALL_PHONE / READ_CONTACTS** – optional; without them the dialer opens / number must be spoken.
- **INTERNET** – Gemini calls.
No storage, location, SMS, accessibility, overlay or "all apps" permissions are used.

## 6. Honest limitations (and the closest alternative)
1. **Wake word is not true always-on.** Android has no API for third-party always-listening; this app loops the speech recognizer in a foreground service.
   It costs battery, Vivo's background manager may kill it, the recognizer can beep, and Google's service may receive audio. Android also limits mic use when the screen is off (default setting: pause when screen off).
   *Alternative:* the mic button (always works) or **Picovoice Porcupine** for a real offline engine (section 9).
2. **Background app launching is blocked on Android 10+.** If YESMIN isn't on screen, "open YouTube/call/music" can't start the app; YESMIN posts a **tap-to-open notification** and tells you so. Torch, volume, notes, reminders and spoken answers still work.
   *Alternative:* becoming Android's default *digital assistant* (VoiceInteractionService) gets different privileges; that is a larger project.
3. **YouTube autoplay:** Android doesn't let apps force a video to play; YESMIN opens the search results for your song.
4. **"Female" voice:** Android doesn't expose voice gender. Pick a voice with Preview; pitch is raised for a cuter tone.
5. **Avatar is a drawn 2.5D character** (gradients, blinking, lip-sync, expressions), not a rigged 3D model. For true 3D see section 10.
6. **Reminders** are inexact (can be a few minutes late) and are cleared if the phone reboots.
7. Bluetooth/Wi-Fi/airplane toggles can't be switched by apps on modern Android; YESMIN opens the correct panel instead.
8. Hindi speech in Devanagari: rule-based commands cover common phrases; everything else is understood by Gemini.

## 7. Secure API-key handling
- **Never hard-coded.** The user types it into Settings. It is encrypted with an **AES-256-GCM key held in the Android Keystore** (`SecureStore`) and sent only in the `x-goog-api-key` header over HTTPS. `allowBackup=false` keeps it out of cloud backups. Don't paste it in screenshots or git.
- **Limits of on-device keys:** on a rooted/compromised phone, a key the app can use can be extracted. Fine for personal use ("bring your own key").
- **For a public app, use a backend proxy:** the app signs in the user (Firebase Auth) + proves it's your genuine app (**Firebase App Check / Play Integrity**) and calls *your* endpoint (Cloud Run / Cloud Functions). The server holds the Gemini key in Secret Manager, applies per-user quotas, and forwards to Gemini. Replace `GeminiClient.base` and the header with your endpoint and a Firebase ID token. Also restrict/rotate the key in Google AI Studio / Cloud console and set budget alerts.

## 8. Using it – example commands
`Yesmin, open YouTube` · `torch on karo` · `volume badhao` / `volume 40` · `Rahul ko call karo` (asks confirmation) ·
`romantic songs bajao` · `play my favourite song` (set it in Settings → Personality) · `open wifi` · `open camera` ·
`note: doodh lana hai` · `mere notes dikhao` · `10 minute baad yaad dilao chai` · `25 times 4` ·
`remember that mujhe chai pasand hai` (needs Memory ON) · `translate to English: ...` · `explain this code ...` · say `bas`/`stop` to end continuous mode.
Wake flow: *"Yesmin"* → "Ji Boss! ❤️ Main yahin hoon. Bataiye kya karna hai?" → speak your command (or "Yesmin open YouTube" in one sentence).

## 9. Optional: real offline wake word with Porcupine
1. Create a free account at console.picovoice.ai, copy your **AccessKey**, train a custom keyword "Yesmin" for Android, download the `.ppn`, put it in `app/src/main/assets/`.
2. `implementation("ai.picovoice:porcupine-android:3.0.2")` (check the latest version).
3. In `WakeWordService`, replace the `listenForWakeWord()` loop with:
```kotlin
val porcupine = PorcupineManager.Builder()
    .setAccessKey(accessKeyFromSecureStore)
    .setKeywordPath("yesmin_android.ppn")
    .build(applicationContext) { /* keyword detected */ scope.launch { engine.wakeTurn("") } }
porcupine.start()   // runs on-device, tiny CPU use; call stop()/delete() in onDestroy
```
Pause Porcupine while the SpeechRecognizer is listening (both need the microphone). Picovoice is an external service with its own license terms.

## 10. Upgrade to a real 3D avatar
Export a rigged character with blendshapes (VRM / glTF, e.g. from VRoid Studio or Ready Player Me, respecting each licence), render it with **Google Filament** or SceneView in Compose, and drive blendshapes from `AssistantEngine.avatarState`, `TtsManager.mouthKick` (lip-sync) and a blink timer. The state flows are already exposed for this.

## 11. Troubleshooting
- *Sync fails:* File → Settings → Build → Gradle → Gradle JDK = 17+. Check internet.
- *"Unresolved reference" / compile errors:* copy the exact message to Claude.
- *Test connection says 404/model not found:* use "Fetch available models" and choose one. *403/400 key error:* re-paste the key. *429:* free-tier quota, wait.
- *Mic does nothing:* accept consent dialog + permission; make sure the Google app / speech service is enabled. Recognizer "busy" = another app uses the mic.
- *Wake word stops after a while (Vivo):* allow background power consumption + Autostart, lock app in Recents, disable battery optimisation (Settings → Wake word).
- *No voice:* Android Settings → Text-to-speech → install Google engine + English (India)/Hindi voice data.
