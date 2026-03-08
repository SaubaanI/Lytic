package com.example.lyticandroid


import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.math.ceil
import kotlin.math.max


class PresageTestActivity : AppCompatActivity() {


   // Change this to your real endpoint that receives final Android biometric payload
   private var uploadUrl = "https://gesticulatory-unenvious-sharonda.ngrok-free.dev/session"
   private var configUrl = "https://gesticulatory-unenvious-sharonda.ngrok-free.dev/session/config/"


   private val apiKey = "g9DATUpRNc3K66UrRtSYt2KG0isRSzts2TWGPC84"


   private lateinit var smartSpectraView: SmartSpectraView
   private lateinit var statusText: TextView
   private lateinit var pulseText: TextView
   private lateinit var breathingText: TextView
   private lateinit var sessionIdText: TextView


   private val mainHandler = Handler(Looper.getMainLooper())
   private val httpClient = OkHttpClient()


   private var sessionSaved = false
   private var sessionClockStarted = false
   private var sessionClockStartMs: Long = 0L


   // These now come dynamically from frontend/backend
   private var sessionId: String = ""
   private var durationSeconds: Int = 60
   private var startBufferMs: Int = 1500
   private var stopBufferMs: Int = 1500


   // Computed dynamically
   private var captureWindowMs: Long = 57000L
   private var maxSeconds: Int = 57


   private var finishRunnable: Runnable? = null


   private val sessionMetrics = mutableListOf<MetricPoint>()
   private val secondToReading = mutableMapOf<Int, MetricPoint>()


   data class MetricPoint(
       val timestampMs: Int,
       val pulseRate: Float? = null,
       val pulseConfidence: Float? = null,
       val breathingRate: Float? = null,
       val breathingConfidence: Float? = null
   )


   data class SessionExport(
       val sessionId: String,
       val deviceTimeZone: String,
       val metrics: List<MetricPoint>
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
       sessionIdText = findViewById(R.id.session_id_text)


       Log.d("SESSION_FLOW", "Intent Extras Check:")
       intent.extras?.keySet()?.forEach { key ->
           Log.d("SESSION_FLOW", "  Extra $key = ${intent.extras?.get(key)}")
       }


       loadSessionConfigFromIntent()
       
       if (sessionId.isNotBlank()) {
           fetchSessionConfig()
       } else {
           recalculateCaptureWindow()
           resetSession()
       }


       Log.d("SESSION_FLOW", "Activity created")
       Log.d("SESSION_FLOW", "SmartSpectra mode = CONTINUOUS")
       Log.d("SESSION_UPLOAD", "uploadUrl = $uploadUrl")
       Log.d(
           "SESSION_CONFIG",
           "sessionId=$sessionId durationSeconds=$durationSeconds " +
                   "startBufferMs=$startBufferMs stopBufferMs=$stopBufferMs " +
                   "captureWindowMs=$captureWindowMs maxSeconds=$maxSeconds"
       )
   }


   private fun loadSessionConfigFromIntent() {
       // 1. Try to get it from Extras
       sessionId = (intent.getStringExtra("session_id") ?: "").trim()
       
       // 2. Try to get it from Deep Link URI (lytic://session/ID)
       if (sessionId.isBlank()) {
           intent.data?.let { uri ->
               Log.d("SESSION_FLOW", "Extracting ID from URI: $uri")
               sessionId = uri.lastPathSegment ?: ""
           }
       }


       if (sessionId.isBlank()) {
           Log.w("SESSION_FLOW", "WARNING: sessionId ('session_id') is missing from intent! Using fallback for debug.")
           sessionId = "manual_debug_${System.currentTimeMillis()}"
       }
       
       durationSeconds = intent.getIntExtra("duration_seconds", 60)
       startBufferMs = intent.getIntExtra("start_buffer_ms", 1500)
       stopBufferMs = intent.getIntExtra("stop_buffer_ms", 1500)
   }


   private fun fetchSessionConfig() {
       Log.d("SESSION_CONFIG", "Fetching config for $sessionId from $configUrl$sessionId")
       val request = Request.Builder()
           .url("$configUrl$sessionId")
           .build()


       httpClient.newCall(request).enqueue(object : okhttp3.Callback {
           override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
               Log.e("SESSION_CONFIG", "Failed to fetch config: ${e.message}")
               runOnUiThread {
                   statusText.text = "Status: config fetch failed"
               }
           }


           override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
               response.use {
                   if (!it.isSuccessful) {
                       Log.e("SESSION_CONFIG", "Config fetch unsuccessful: ${it.code}")
                       return
                   }
                   val body = it.body?.string() ?: return
                   try {
                       val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
                       
                       durationSeconds = parsed.get("duration_seconds").asInt
                       startBufferMs = parsed.get("start_buffer_ms").asInt
                       stopBufferMs = parsed.get("stop_buffer_ms").asInt
                       
                       Log.d("SESSION_CONFIG", "Config received: dur=$durationSeconds start=$startBufferMs stop=$stopBufferMs")
                       
                       runOnUiThread {
                           recalculateCaptureWindow()
                           resetSession()
                       }
                   } catch (e: Exception) {
                       Log.e("SESSION_CONFIG", "Error parsing config: ${e.message}")
                   }
               }
           }
       })
   }


   private fun recalculateCaptureWindow() {
       val totalVideoMs = durationSeconds * 1000L
       val usableMs = totalVideoMs - startBufferMs - stopBufferMs


       captureWindowMs = max(1000L, usableMs)
       maxSeconds = ceil(captureWindowMs / 1000.0).toInt()
   }


   private fun handleMetricsBuffer(metrics: MetricsBuffer) {
       if (sessionSaved) {
           Log.d("SESSION_FLOW", "Ignoring metrics because session already saved")
           return
       }


       val latestPulse = metrics.pulse.rateList.lastOrNull()
       val latestBreathing = metrics.breathing.rateList.lastOrNull()


       val pulseRate = latestPulse?.value
       val pulseConfidence = latestPulse?.confidence
       val breathingRate = latestBreathing?.value
       val breathingConfidence = latestBreathing?.confidence


       val hasUsefulData =
           pulseRate != null ||
                   pulseConfidence != null ||
                   breathingRate != null ||
                   breathingConfidence != null


       runOnUiThread {
           if (hasUsefulData) {
               pulseText.text = "Pulse: ${pulseRate ?: "--"}"
               breathingText.text = "Breathing: ${breathingRate ?: "--"}"
           } else {
               pulseText.text = "Pulse: --"
               breathingText.text = "Breathing: --"
           }
       }


       if (!sessionClockStarted) {
           if (!hasUsefulData) {
               runOnUiThread {
                   statusText.text = "Status: waiting for real measurement"
               }
               return
           }


           sessionClockStarted = true
           sessionClockStartMs = System.currentTimeMillis()


           val totalRunMs = startBufferMs.toLong() + captureWindowMs


           Log.d(
               "SESSION_FLOW",
               "First real measurement received. " +
                       "Session clock started. totalRunMs=$totalRunMs"
           )


           finishRunnable = Runnable {
               Log.d("SESSION_FLOW", "Capture window complete, finishing session")
               finishSession()
           }
           mainHandler.postDelayed(finishRunnable!!, totalRunMs)
       }


       val totalElapsedMs = System.currentTimeMillis() - sessionClockStartMs


       // Still inside start buffer, do not capture yet
       if (totalElapsedMs < startBufferMs) {
           val remaining = startBufferMs - totalElapsedMs
           runOnUiThread {
               statusText.text = "Status: waiting start buffer (${remaining}ms left)"
           }
           Log.d("SESSION_CAPTURE", "Inside start buffer, skipping capture")
           return
       }


       val captureElapsedMs = totalElapsedMs - startBufferMs


       if (captureElapsedMs >= captureWindowMs) {
           Log.d("SESSION_CAPTURE", "Past capture window, ignoring callback")
           return
       }


       val tSec = (captureElapsedMs / 1000L).toInt()


       if (tSec !in 0 until maxSeconds) {
           Log.d("SESSION_CAPTURE", "Ignoring callback outside valid second window: tSec=$tSec")
           return
       }


       if (!hasUsefulData) {
           Log.d("SESSION_CAPTURE", "No useful data at tSec=$tSec")
           return
       }


       Log.d(
           "PRESAGE_RAW",
           "sessionId=$sessionId tSec=$tSec " +
                   "pulse=$pulseRate pulseConf=$pulseConfidence " +
                   "breathing=$breathingRate breathingConf=$breathingConfidence"
       )


       secondToReading[tSec] = MetricPoint(
           timestampMs = tSec * 1000,
           pulseRate = pulseRate,
           pulseConfidence = pulseConfidence,
           breathingRate = breathingRate,
           breathingConfidence = breathingConfidence
       )


       Log.d(
           "SESSION_CAPTURE",
           "Captured reading for sec=$tSec/$maxSeconds " +
                   "pulse=$pulseRate pulseConf=$pulseConfidence " +
                   "breathing=$breathingRate breathingConf=$breathingConfidence"
       )


       runOnUiThread {
           statusText.text = "Status: measuring second ${tSec + 1}/$maxSeconds"
       }
   }


   private fun buildFinalMetrics() {
       sessionMetrics.clear()


       var lastKnownMetric: MetricPoint? = null


       for (sec in 0 until maxSeconds) {
           val metricForThisSecond = secondToReading[sec]


           val finalMetric = when {
               metricForThisSecond != null -> {
                   lastKnownMetric = metricForThisSecond
                   metricForThisSecond
               }
               lastKnownMetric != null -> {
                   MetricPoint(
                       timestampMs = sec * 1000,
                       pulseRate = lastKnownMetric!!.pulseRate,
                       pulseConfidence = lastKnownMetric!!.pulseConfidence,
                       breathingRate = lastKnownMetric!!.breathingRate,
                       breathingConfidence = lastKnownMetric!!.breathingConfidence
                   )
               }
               else -> {
                   MetricPoint(
                       timestampMs = sec * 1000,
                       pulseRate = null,
                       pulseConfidence = null,
                       breathingRate = null,
                       breathingConfidence = null
                   )
               }
           }


           sessionMetrics.add(finalMetric)


           Log.d(
               "SESSION_FINAL",
               "sec=${finalMetric.timestampMs} " +
                       "pulse=${finalMetric.pulseRate} pulseConf=${finalMetric.pulseConfidence} " +
                       "breathing=${finalMetric.breathingRate} breathingConf=${finalMetric.breathingConfidence}"
           )
       }
   }


   private fun saveSessionToJsonFile() {
       if (sessionSaved) {
           Log.d("SESSION_JSON", "saveSessionToJsonFile skipped because already saved")
           return
       }


       sessionSaved = true


       if (sessionId.isBlank()) {
           Log.e("SESSION_UPLOAD", "ABORT: sessionId is empty. Cannot upload.")
       }


       val export = SessionExport(
           sessionId = sessionId,
           deviceTimeZone = TimeZone.getDefault().id,
           metrics = sessionMetrics.toList()
       )


       val gson = GsonBuilder()
           .serializeNulls()
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
           statusText.text = "Status: saved ${sessionMetrics.size} samples"
       }
   }


   private fun uploadSessionJson(json: String) {
       Log.d("SESSION_UPLOAD", "Starting upload to $uploadUrl")


       val requestBody = json.toRequestBody("application/json".toMediaType())


       val request = Request.Builder()
           .url(uploadUrl)
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
           finishRunnable?.let { mainHandler.removeCallbacks(it) }
           finishRunnable = null
       } catch (e: Exception) {
           Log.e("SESSION_FLOW", "Failed removing finish runnable: ${e.message}", e)
       }


       buildFinalMetrics()


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


   private fun resetSession() {
       sessionSaved = false
       sessionClockStarted = false
       sessionClockStartMs = 0L


       sessionMetrics.clear()
       secondToReading.clear()


       finishRunnable?.let { mainHandler.removeCallbacks(it) }
       finishRunnable = null


       runOnUiThread {
           smartSpectraView.visibility = View.VISIBLE
           statusText.text =
               "Status: ready | duration=${durationSeconds}s | startBuffer=${startBufferMs}ms | stopBuffer=${stopBufferMs}ms | capture=${maxSeconds}s"
           pulseText.text = "Pulse: --"
           breathingText.text = "Breathing: --"
           try {
               sessionIdText.text = "Session: $sessionId"
           } catch (e: Exception) {}
       }


       Log.d(
           "SESSION_FLOW",
           "resetSession sessionId=$sessionId durationSeconds=$durationSeconds " +
                   "startBufferMs=$startBufferMs stopBufferMs=$stopBufferMs maxSeconds=$maxSeconds"
       )
   }


   override fun onDestroy() {
       super.onDestroy()


       finishRunnable?.let { mainHandler.removeCallbacks(it) }
       finishRunnable = null


       Log.d("SESSION_FLOW", "Activity destroyed")
   }
}
