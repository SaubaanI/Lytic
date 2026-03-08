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


   // Biometric upload endpoint
   private var uploadUrl = "https://gesticulatory-unenvious-sharonda.ngrok-free.dev/session"
   private val apiKey = "g9DATUpRNc3K66UrRtSYt2KG0isRSzts2TWGPC84"


   private lateinit var smartSpectraView: SmartSpectraView
   private lateinit var statusText: TextView
   private lateinit var pulseText: TextView
   private lateinit var breathingText: TextView


   private val mainHandler = Handler(Looper.getMainLooper())
   private val httpClient = OkHttpClient()


   private var sessionSaved = false
   private var sessionClockStarted = false
   private var sessionClockStartMs: Long = 0L


   // Default capture settings
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
       val sessionId: String? = null,
       val deviceTimeZone: String,
       val metrics: List<MetricPoint>
   )


   private var smartSpectraSdk: SmartSpectraSdk? = null


   override fun onCreate(savedInstanceState: Bundle?) {
       super.onCreate(savedInstanceState)
       
       try {
           setContentView(R.layout.activity_presage_test)


           smartSpectraView = findViewById(R.id.smart_spectra_view)
           statusText = findViewById(R.id.status_text)
           pulseText = findViewById(R.id.pulse_text)
           breathingText = findViewById(R.id.breathing_text)


           recalculateCaptureWindow()
           resetSession()


           // CRITICAL: Initialize SDK *after* views are linked to avoid UninitializedPropertyAccessException
           initSdk()


           Log.d("SESSION_FLOW", "Activity created (Simplified Flow)")
           Log.d("SESSION_FLOW", "SmartSpectra mode = CONTINUOUS")
           Log.d("SESSION_UPLOAD", "uploadUrl = $uploadUrl")
       } catch (e: Exception) {
           Log.e("SESSION_FLOW", "CRITICAL ERROR in onCreate: ${e.message}", e)
       }
   }


   private fun initSdk() {
       try {
           smartSpectraSdk = SmartSpectraSdk.getInstance().apply {
               setApiKey(apiKey)
               setSmartSpectraMode(SmartSpectraMode.CONTINUOUS)
               setMetricsBufferObserver { metricsBuffer ->
                   handleMetricsBuffer(metricsBuffer)
               }
           }
           Log.d("SESSION_FLOW", "SmartSpectraSdk Initialized")
       } catch (e: Exception) {
           Log.e("SESSION_FLOW", "Failed to initialize SmartSpectraSdk: ${e.message}", e)
       }
   }


   private fun recalculateCaptureWindow() {
       val totalVideoMs = durationSeconds * 1000L
       val usableMs = totalVideoMs - startBufferMs - stopBufferMs


       captureWindowMs = max(1000L, usableMs)
       maxSeconds = ceil(captureWindowMs / 1000.0).toInt()
   }


   private fun handleMetricsBuffer(metrics: MetricsBuffer) {
       if (sessionSaved) return


       val latestPulse = metrics.pulse.rateList.lastOrNull()
       val latestBreathing = metrics.breathing.rateList.lastOrNull()


       val pulseRate = latestPulse?.value
       val pulseConfidence = latestPulse?.confidence
       val breathingRate = latestBreathing?.value
       val breathingConfidence = latestBreathing?.confidence


       val hasUsefulData = pulseRate != null || pulseConfidence != null ||
                           breathingRate != null || breathingConfidence != null


       runOnUiThread {
           // If hasUsefulData, show real values, else show --
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


           Log.d("SESSION_FLOW", "First real measurement received. totalRunMs=$totalRunMs")


           finishRunnable = Runnable {
               Log.d("SESSION_FLOW", "Capture window complete, finishing session")
               finishSession()
           }
           mainHandler.postDelayed(finishRunnable!!, totalRunMs)
       }


       val totalElapsedMs = System.currentTimeMillis() - sessionClockStartMs
       if (totalElapsedMs < startBufferMs) {
           val remaining = startBufferMs - totalElapsedMs
           runOnUiThread {
               statusText.text = "Status: waiting start buffer (${remaining}ms left)"
           }
           return
       }


       val captureElapsedMs = totalElapsedMs - startBufferMs
       if (captureElapsedMs >= captureWindowMs) return


       val tSec = (captureElapsedMs / 1000L).toInt()
       if (tSec !in 0 until maxSeconds) return
       if (!hasUsefulData) return


       secondToReading[tSec] = MetricPoint(
           timestampMs = tSec * 1000,
           pulseRate = pulseRate,
           pulseConfidence = pulseConfidence,
           breathingRate = breathingRate,
           breathingConfidence = breathingConfidence
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
                   MetricPoint(timestampMs = sec * 1000)
               }
           }
           sessionMetrics.add(finalMetric)
       }
   }


   private fun saveSessionToJsonFile() {
       if (sessionSaved) return
       sessionSaved = true


       val export = SessionExport(
           sessionId = null,
           deviceTimeZone = TimeZone.getDefault().id,
           metrics = sessionMetrics.toList()
       )


       val json = GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(export)
       val file = File(filesDir, "session_${System.currentTimeMillis()}.json")
       file.writeText(json)


       Log.d("SESSION_JSON", "Saved to: ${file.absolutePath}")
       uploadSessionJson(json)


       runOnUiThread {
           statusText.text = "Status: saved ${sessionMetrics.size} samples"
       }
   }


   private fun uploadSessionJson(json: String) {
       val requestBody = json.toRequestBody("application/json".toMediaType())
       val request = Request.Builder().url(uploadUrl).post(requestBody).build()


       Thread {
           try {
               httpClient.newCall(request).execute().use { response ->
                   Log.d("SESSION_UPLOAD", "code=${response.code} body=${response.body?.string()}")
               }
           } catch (e: Exception) {
               Log.e("SESSION_UPLOAD", "Upload failed: ${e.message}", e)
           }
       }.start()
   }


   private fun finishSession() {
       if (sessionSaved) return
       Log.d("SESSION_FLOW", "Finishing session")


       try {
           finishRunnable?.let { mainHandler.removeCallbacks(it) }
           finishRunnable = null
       } catch (e: Exception) {}


       buildFinalMetrics()
       runOnUiThread {
           statusText.text = "Status: finishing session"
       }


       try {
           smartSpectraView.visibility = View.GONE
       } catch (e: Exception) {}


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
           statusText.text = "Status: ready | duration=${durationSeconds}s | capture=${maxSeconds}s"
           pulseText.text = "Pulse: --"
           breathingText.text = "Breathing: --"
       }
   }


   override fun onDestroy() {
       super.onDestroy()
       finishRunnable?.let { mainHandler.removeCallbacks(it) }
       finishRunnable = null
       Log.d("SESSION_FLOW", "Activity destroyed")
   }
}
