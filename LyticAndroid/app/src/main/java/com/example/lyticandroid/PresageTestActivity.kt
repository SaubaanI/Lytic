package com.example.lyticandroid

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.presage.physiology.proto.MetricsProto.MetricsBuffer
import com.presagetech.smartspectra.SmartSpectraMode
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.SmartSpectraView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.TimeZone

class PresageTestActivity : AppCompatActivity() {

private val backendUrl = "https://gesticulatory-unenvious-sharonda.ngrok-free.dev/session"
private val apiKey = "g9DATUpRNc3K66UrRtSYt2KG0isRSzts2TWGPC84"

private lateinit var smartSpectraView: SmartSpectraView
private lateinit var statusText: TextView
private lateinit var pulseText: TextView
private lateinit var breathingText: TextView

private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
private val httpClient = OkHttpClient()

private var sessionSaved = false
private var measurementStarted = false
private var samplingStarted = false

private var adId: String = "test_ad_001"

// Latest live values coming from SDK
private var latestPulseRate: Float? = null
private var latestPulseConfidence: Float? = null
private var latestBreathingRate: Float? = null
private var latestBreathingConfidence: Float? = null

// Track when SDK last updated the values
private var lastMetricsUpdateMs: Long = 0L

private var sampleCount = 0
private val maxSamples = 30

private var samplingRunnable: Runnable? = null

// Raw per-second frozen snapshots
private val secondSnapshots = mutableListOf<SecondSnapshot>()

data class SecondSnapshot(
val tSec: Int,
val timestampMs: Long,
val pulseRate: Float? = null,
val pulseConfidence: Float? = null,
val breathingRate: Float? = null,
val breathingConfidence: Float? = null,
val sourceUpdatedAtMs: Long? = null
)

data class SessionMetric(
val tSec: Int,
val timestampMs: Long,
val pulseRate: Float? = null,
val pulseConfidence: Float? = null,
val breathingRate: Float? = null,
val breathingConfidence: Float? = null
)

data class SessionExport(
val adId: String,
val deviceTimeZone: String,
val metrics: List<SessionMetric>
)

private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.getInstance().apply {
setApiKey(apiKey)
setSmartSpectraMode(SmartSpectraMode.CONTINUOUS)

setMetricsBufferObserver { metricsBuffer ->
handleMetricsBuffer(metricsBuffer)
}
}

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
setContentView(R.layout.activity_presage_test)

smartSpectraView = findViewById(R.id.smart_spectra_view)
statusText = findViewById(R.id.status_text)
pulseText = findViewById(R.id.pulse_text)
breathingText = findViewById(R.id.breathing_text)

resetSession("test_ad_001")

Log.d("SESSION_FLOW", "Activity created")
Log.d("SESSION_UPLOAD", "backendUrl = $backendUrl")
}

private fun handleMetricsBuffer(metrics: MetricsBuffer) {
if (sessionSaved) {
Log.d("SESSION_FLOW", "Ignoring metrics because session already saved")
return
}

val latestPulse = metrics.pulse.rateList.lastOrNull()
val latestBreathing = metrics.breathing.rateList.lastOrNull()

val newPulseRate = latestPulse?.value
val newPulseConfidence = latestPulse?.confidence
val newBreathingRate = latestBreathing?.value
val newBreathingConfidence = latestBreathing?.confidence

val hasUsefulData =
newPulseRate != null ||
newPulseConfidence != null ||
newBreathingRate != null ||
newBreathingConfidence != null

if (hasUsefulData) {
latestPulseRate = newPulseRate
latestPulseConfidence = newPulseConfidence
latestBreathingRate = newBreathingRate
latestBreathingConfidence = newBreathingConfidence
lastMetricsUpdateMs = System.currentTimeMillis()
}

runOnUiThread {
if (hasUsefulData) {
statusText.text = if (samplingStarted) {
"Status: measuring ($sampleCount/$maxSamples)"
} else {
"Status: first measurement received"
}
pulseText.text = "Pulse: ${latestPulseRate ?: "--"}"
breathingText.text = "Breathing: ${latestBreathingRate ?: "--"}"
} else {
statusText.text = "Status: waiting for real measurement"
pulseText.text = "Pulse: --"
breathingText.text = "Breathing: --"
}
}

if (!measurementStarted && hasUsefulData) {
measurementStarted = true
Log.d("SESSION_FLOW", "First real measurement received")
startSamplingLoop()
}
}

private fun startSamplingLoop() {
if (samplingStarted) return

samplingStarted = true
sampleCount = 0
secondSnapshots.clear()

Log.d("SESSION_FLOW", "Starting 30-second sampling loop")

samplingRunnable = object : Runnable {
override fun run() {
if (sessionSaved) {
Log.d("SESSION_FLOW", "Sampling loop stopped because session already saved")
return
}

val nextSecond = sampleCount + 1
val snapshot = createSnapshotForSecond(nextSecond)
secondSnapshots.add(snapshot)
sampleCount++

Log.d(
"SESSION_SAMPLE",
"Saved sample ${snapshot.tSec}/$maxSamples " +
"timestampMs=${snapshot.timestampMs} " +
"sourceUpdatedAtMs=${snapshot.sourceUpdatedAtMs} " +
"pulse=${snapshot.pulseRate} pulseConf=${snapshot.pulseConfidence} " +
"breathing=${snapshot.breathingRate} breathingConf=${snapshot.breathingConfidence}"
)

runOnUiThread {
statusText.text = "Status: measuring ($sampleCount/$maxSamples)"
pulseText.text = "Pulse: ${snapshot.pulseRate ?: "--"}"
breathingText.text = "Breathing: ${snapshot.breathingRate ?: "--"}"
}

if (secondSnapshots.size >= maxSamples) {
finishSession()
} else {
mainHandler.postDelayed(this, 1000L)
}
}
}

// First capture at second 1, then 2 ... 30
mainHandler.postDelayed(samplingRunnable!!, 1000L)
}

/**
* Freezes the current SDK values into a brand-new object for this specific second.
* This guarantees each loop iteration stores its own snapshot in the array.
*/
private fun createSnapshotForSecond(tSec: Int): SecondSnapshot {
val nowMs = System.currentTimeMillis()

return SecondSnapshot(
tSec = tSec,
timestampMs = nowMs,
pulseRate = latestPulseRate,
pulseConfidence = latestPulseConfidence,
breathingRate = latestBreathingRate,
breathingConfidence = latestBreathingConfidence,
sourceUpdatedAtMs = if (lastMetricsUpdateMs == 0L) null else lastMetricsUpdateMs
)
}

/**
* Converts the 30 stored snapshots into the final JSON-ready dataset.
*/
private fun buildSessionExportFromSnapshots(): SessionExport {
val metrics = secondSnapshots.map { snapshot ->
SessionMetric(
tSec = snapshot.tSec,
timestampMs = snapshot.timestampMs,
pulseRate = snapshot.pulseRate,
pulseConfidence = snapshot.pulseConfidence,
breathingRate = snapshot.breathingRate,
breathingConfidence = snapshot.breathingConfidence
)
}

return SessionExport(
adId = adId,
deviceTimeZone = TimeZone.getDefault().id,
metrics = metrics
)
}

private fun saveSessionToJsonFile() {
if (sessionSaved) {
Log.d("SESSION_JSON", "saveSessionToJsonFile skipped because already saved")
return
}

sessionSaved = true

val export = buildSessionExportFromSnapshots()

val gson = GsonBuilder()
.setPrettyPrinting()
.create()

val json = gson.toJson(export)

val file = File(filesDir, "session_${System.currentTimeMillis()}.json")
file.writeText(json)

Log.d("SESSION_JSON", "SAVING NOW")
Log.d("SESSION_JSON", json)
Log.d("SESSION_JSON", "Saved to: ${file.absolutePath}")

uploadSessionJson(json)

runOnUiThread {
statusText.text = "Status: saved ${secondSnapshots.size} samples"
}
}

private fun uploadSessionJson(json: String) {
Log.d("SESSION_UPLOAD", "Starting upload to $backendUrl")

val requestBody = json.toRequestBody("application/json".toMediaType())

val request = Request.Builder()
.url(backendUrl)
.post(requestBody)
.build()

Thread {
try {
httpClient.newCall(request).execute().use { response ->
val responseBody = response.body?.string()
Log.d("SESSION_UPLOAD", "code=${response.code} body=$responseBody")
}
} catch (e: Exception) {
Log.e("SESSION_UPLOAD", "Upload failed: ${e.message}", e)
}
}.start()
}

private fun finishSession() {
if (sessionSaved) {
Log.d("SESSION_FLOW", "finishSession skipped because already saved")
return
}

Log.d("SESSION_FLOW", "Finishing session")

try {
samplingRunnable?.let { mainHandler.removeCallbacks(it) }
samplingRunnable = null
} catch (e: Exception) {
Log.e("SESSION_FLOW", "Failed removing sampling loop: ${e.message}", e)
}

runOnUiThread {
statusText.text = "Status: finishing session"
}

try {
smartSpectraView.visibility = View.GONE
} catch (e: Exception) {
Log.e("SESSION_FLOW", "Failed hiding SmartSpectraView: ${e.message}", e)
}

saveSessionToJsonFile()
}

private fun resetSession(newAdId: String) {
adId = newAdId

sessionSaved = false
measurementStarted = false
samplingStarted = false
sampleCount = 0

latestPulseRate = null
latestPulseConfidence = null
latestBreathingRate = null
latestBreathingConfidence = null
lastMetricsUpdateMs = 0L

secondSnapshots.clear()

samplingRunnable?.let { mainHandler.removeCallbacks(it) }
samplingRunnable = null

runOnUiThread {
smartSpectraView.visibility = View.VISIBLE
statusText.text = "Status: ready to scan"
pulseText.text = "Pulse: --"
breathingText.text = "Breathing: --"
}

Log.d("SESSION_FLOW", "resetSession adId=$adId")
}

override fun onDestroy() {
super.onDestroy()

samplingRunnable?.let { mainHandler.removeCallbacks(it) }
samplingRunnable = null

Log.d("SESSION_FLOW", "Activity destroyed")
}
}
