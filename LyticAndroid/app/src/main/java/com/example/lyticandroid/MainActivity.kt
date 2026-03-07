package com.example.lyticandroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class MainActivity : ComponentActivity() {

    private val baseUrl = "https://gesticulatory-unenvious-sharonda.ngrok-free.dev"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("API_TEST", "App started")

        setContent {
            androidx.compose.material3.Text("Lytic Android is running")
        }

        testBackend()
    }

    private fun testBackend() {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(baseUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API_TEST", "Request failed: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("API_TEST", response.body?.string() ?: "No response body")
            }
        })
    }
}