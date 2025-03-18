package com.example.dynamicsignaturecapture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import android.widget.EditText
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.github.gcacace.signaturepad.views.SignaturePad
import android.graphics.Bitmap
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.widget.TextView
import org.w3c.dom.Text
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

data class StrokePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val pressure: Float
)

data class StrokeMetric(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val velocity: Float,
    val acceleration: Float,
    val direction: Float?,
    val pressure: Float,
    val strokeNumber: Int
)


class MainActivity : AppCompatActivity() {
    private lateinit var signaturePad: SignaturePad
    private val strokeMetrics = mutableListOf<StrokeMetric>()
    private val currentStroke = mutableListOf<StrokePoint>()
    private var currentStrokeNumber = 0
    private var lastPoint: StrokePoint? = null
    private var attemptNumber = 1
    private var username: String = ""
    private var isForged: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val attemptPicker: NumberPicker = findViewById(R.id.attempt_picker)
        attemptPicker.minValue = 1
        attemptPicker.maxValue = 20

        signaturePad = findViewById(R.id.signature_pad)

        val clearButton: Button = findViewById(R.id.clear_button)
        val saveButton: Button = findViewById(R.id.save_button)
        val backButton: Button = findViewById(R.id.back_button)
        val usernameDisplay: TextView = findViewById(R.id.display_username)
        val statusText: TextView = findViewById(R.id.status_text)

        isForged = intent.getBooleanExtra("SELECTED_TYPE", false)
        username = intent.getStringExtra("USERNAME") ?: "Unknown user"
        usernameDisplay.text = "User: $username"
        Log.d("type=", isForged.toString())
        statusText.text = if (isForged) "Forged" else "Genuine"


        clearButton.setOnClickListener {
            signaturePad.clear()
            strokeMetrics.clear()
            currentStrokeNumber = 0
            lastPoint = null
            Toast.makeText(this, "Signature cleared", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            attemptNumber = attemptPicker.value
            saveSignature()
        }

        backButton.setOnClickListener {
            val intent = Intent(this, UserEntryActivity::class.java)
            startActivity(intent)
            finish()
        }

        signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {
                currentStroke.clear()
                currentStrokeNumber++
            }

            override fun onSigned() {
                processTouchPoints()
            }

            override fun onClear() {
                strokeMetrics.clear()
                currentStrokeNumber = 0
                lastPoint = null
            }
        })


        signaturePad.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val pressure = event.size * 10
                    val point = StrokePoint(event.x, event.y, System.currentTimeMillis(), pressure)
                    currentStroke.add(point)
                    lastPoint?.let { last ->
                        val velocity = calculateVelocity(last, point)
                        val direction = calculateDirection(last, point)
                        val acceleration = if (strokeMetrics.isNotEmpty()) {
                            calculateAcceleration(strokeMetrics.last().velocity, velocity, (point.timestamp - last.timestamp) / 1000f)
                        } else 0f
                        strokeMetrics.add(
                            StrokeMetric(point.timestamp, point.x, point.y, velocity, acceleration, direction, point.pressure, currentStrokeNumber)
                        )
                    }
                    lastPoint = point
                }
                MotionEvent.ACTION_UP -> lastPoint = null
            }
            false
        }
    }

    private fun processTouchPoints() {
        if (currentStroke.size < 2) return
        for (i in 1 until currentStroke.size) {
            val point1 = currentStroke[i - 1]
            val point2 = currentStroke[i]
            val velocity = calculateVelocity(point1, point2)
            val direction = calculateDirection(point1, point2)
            val acceleration = if (strokeMetrics.isNotEmpty()) {
                calculateAcceleration(strokeMetrics.last().velocity, velocity, (point2.timestamp - point1.timestamp) / 1000f)
            } else 0f
            strokeMetrics.add(StrokeMetric(point2.timestamp, point2.x, point2.y, velocity, acceleration, direction, point2.pressure, currentStrokeNumber))
        }
        currentStroke.clear()
    }

    private fun calculateVelocity(p1: StrokePoint, p2: StrokePoint): Float {
        val distance = sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
        val timeDiff = (p2.timestamp - p1.timestamp) / 1000f
        return if (timeDiff != 0f) distance / timeDiff else 0f
    }

    private fun calculateAcceleration(v1: Float, v2: Float, timeDiff: Float): Float {
        return if (timeDiff != 0f) (v2 - v1) / timeDiff else 0f
    }

    private fun calculateDirection(p1: StrokePoint, p2: StrokePoint): Float {
        return atan2((p2.y - p1.y), (p2.x - p1.x)) * (180 / Math.PI).toFloat()
    }

    private fun saveSignature() {
        val resolver = contentResolver
        val bitmap = signaturePad.signatureBitmap
        try {
            val suffix = if (isForged) "-forged" else ""
            val imageValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$username-signature-$attemptNumber$suffix.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Signatures")
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues)?.let { uri ->
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 80, it) }
            } ?: throw IOException("Failed to create image file")

            val metricsValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$username-signature-metrics-$attemptNumber$suffix.csv")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "text/csv")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/SignatureMetrics")
            }
            resolver.insert(MediaStore.Files.getContentUri("external"), metricsValues)?.let { uri ->
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write("Timestamp,X,Y,Velocity,Acceleration,Direction,Pressure,StrokeNumber\n")
                    strokeMetrics.forEach { writer.write("${it.timestamp},${it.x},${it.y},${it.velocity},${it.acceleration},${it.direction ?: ""},${it.pressure},${it.strokeNumber}\n") }
                }
            } ?: throw IOException("Failed to create metrics file")

            Toast.makeText(this, "Signature & Metrics Saved!", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}