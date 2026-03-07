package com.example.lyticandroid

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.SmartSpectraView
import com.presage.physiology.proto.MetricsProto.MetricsBuffer
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File
import com.presagetech.smartspectra.SmartSpectraMode
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PresageTestActivity : AppCompatActivity() {

    private var sessionSaved = false
    private var saveScheduled = false
    private val backendUrl = "https://gesticulatory-unenvious-sharonda.ngrok-free.dev/session"
    private lateinit var smartSpectraView: SmartSpectraView
    private lateinit var statusText: TextView
    private lateinit var pulseText: TextView
    private lateinit var breathingText: TextView

    data class SessionMetric(
        val timestampMs: Long,
        val pulseRate: Float?,
        val pulseConfidence: Float?,
        val breathingRate: Float?,
        val breathingConfidence: Float?
    )

    data class SessionExport(
        val adId: String,
        val sessionStartedAtMs: Long,
        val sampleCount: Int,
        val deviceTimeZone: String,
        val metrics: List<SessionMetric>
    )

    private var apiKey = "g9DATUpRNc3K66UrRtSYt2KG0isRSzts2TWGPC84"

    private val sessionMetrics = mutableListOf<SessionMetric>()
    private var sessionStartTime = 0L

    private val smartSpectraSdk: SmartSpectraSdk = SmartSpectraSdk.getInstance().apply {
        setApiKey(apiKey)
        setSmartSpectraMode(SmartSpectraMode.CONTINUOUS)
        setMeasurementDuration(30.0)
        setRecordingDelay(3)

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

        sessionStartTime = System.currentTimeMillis()
        statusText.text = "Status: ready to scan"
        Log.d("SESSION_UPLOAD", "backendUrl = $backendUrl")
    }

    private fun handleMetricsBuffer(metrics: MetricsBuffer) {
        val elapsed = System.currentTimeMillis() - sessionStartTime

        val latestPulse = metrics.pulse.rateList.lastOrNull()
        val latestBreathing = metrics.breathing.rateList.lastOrNull()

        val pulseRate = latestPulse?.value
        val pulseConfidence = latestPulse?.confidence

        val breathingRate = latestBreathing?.value
        val breathingConfidence = latestBreathing?.confidence

        sessionMetrics.add(
            SessionMetric(
                timestampMs = elapsed,
                pulseRate = pulseRate,
                pulseConfidence = pulseConfidence,
                breathingRate = breathingRate,
                breathingConfidence = breathingConfidence
            )
        )

        Log.d(
            "PRESAGE_SESSION",
            "t=$elapsed pulse=$pulseRate pulseConf=$pulseConfidence breathing=$breathingRate breathingConf=$breathingConfidence"
        )

        runOnUiThread {
            statusText.text = "Status: measuring"
            pulseText.text = "Pulse: ${pulseRate ?: "--"}"
            breathingText.text = "Breathing: ${breathingRate ?: "--"}"
        }

        if (!saveScheduled && sessionMetrics.isNotEmpty()){
            saveScheduled = true

            Handler(Looper.getMainLooper()).postDelayed({
                if(!sessionSaved && sessionMetrics.isNotEmpty()){
                    sessionSaved = true
                    saveSessionToJsonFile()
                }
            }, 1000)

        }
    }

    private fun saveSessionToJsonFile() {
        val export = SessionExport(
            adId = "test_ad_01",
            sessionStartedAtMs = sessionStartTime,
            sampleCount = sessionMetrics.size,
            deviceTimeZone = java.util.TimeZone.getDefault().id,
            metrics = sessionMetrics
        )

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
            statusText.text = "Status: saved ${sessionMetrics.size} samples"
        }
    }

    override fun onPause() {
        super.onPause()
        if (sessionMetrics.isNotEmpty()) {
            saveSessionToJsonFile()
        }
    }

    private fun resetSession(){
        sessionSaved = false
        saveScheduled = false
        sessionMetrics.clear()
        sessionStartTime = System.currentTimeMillis()

        runOnUiThread {
            statusText.text = "Status: ready to scan"
            pulseText.text = "Pulse: --"
            breathingText.text = "Breathing: --"
        }
    }

    private fun uploadSessionJson(json: String){
        val client = OkHttpClient()

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(backendUrl)
            .post(requestBody)
            .build()

        Thread{
            try{
                client.newCall(request).execute().use{ response ->
                    val responseBody = response.body?.string()
                    Log.d("SESSION_UPLOAD", "code = ${response.code} body = $responseBody")
                }
            } catch (e: Exception){
                Log.e("SESSION_UPLOAD", "upload failed: ${e.message}", e)
            }
        }.start()
    }
}

