package com.example.dynamicsignaturecapture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private lateinit var signaturePad: SignaturePad
    private val strokeMetrics = mutableListOf<StrokeMetric>()
    private val currentStroke = mutableListOf<StrokePoint>()
    private var currentUserName: String = ""
    private var currentStrokeNumber = 0
    private var lastPoint: StrokePoint? = null
    private var attempNumber = 1


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nameInput: EditText = findViewById(R.id.name_input)
        val attempt: NumberPicker = findViewById(R.id.attempt_picker)
        attempt.setMinValue(1)
        attempt.setMaxValue(20)

        signaturePad = findViewById(R.id.signature_pad)

        val clearButton: Button = findViewById(R.id.clear_button)
        val saveButton: Button = findViewById(R.id.save_button)

        clearButton.setOnClickListener {
            signaturePad.clear()
            currentUserName = ""
            nameInput.text.clear()
            strokeMetrics.clear()
            currentStrokeNumber = 0
            attempt.value = 1
            lastPoint = null
        }
        saveButton.setOnClickListener {
            currentUserName = nameInput.text.toString().trim()
            attempNumber = attempt.value
            if (currentUserName.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (checkPermissions()) {
                saveSignature()
            } else {
                requestPermissions()
            }
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
//            Toast.makeText(this,
//                "${event.pressure} ${event.size}\n",
//                Toast.LENGTH_SHORT).show()
            Log.d("Size", "${event.pressure} - ${event.size * 10}\n")
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
//                    val pressure = when {
//                        event.device.sources and android.view.InputDevice.SOURCE_STYLUS != 0 -> event.pressure
//                        else -> event.size
//                    }
                    val pressure = event.size * 10

                    val point = StrokePoint(
                        x = event.x,
                        y = event.y,
                        timestamp = System.currentTimeMillis(),
                        pressure = pressure
                    )
                    currentStroke.add(point)

                    // Calculate metrics for this point
                    lastPoint?.let { last ->
                        val velocity = calculateVelocity(last, point)
                        val direction = calculateDirection(last, point)
                        val acceleration = if (strokeMetrics.isNotEmpty()) {
                            calculateAcceleration(
                                strokeMetrics.last().velocity,
                                velocity,
                                (point.timestamp - last.timestamp) / 1000f
                            )
                        } else 0f

                        strokeMetrics.add(
                            StrokeMetric(
                                timestamp = point.timestamp,
                                x = point.x,
                                y = point.y,
                                velocity = velocity,
                                acceleration = acceleration,
                                direction = direction,
                                pressure = point.pressure,
                                strokeNumber = currentStrokeNumber
                            )
                        )
                    }
                    lastPoint = point
                }
                MotionEvent.ACTION_UP -> {
                    lastPoint = null
                }
            }
            false
        }

    }

    private fun processTouchPoints(){
        if (currentStroke.size < 2) return

        for (i in 1 until currentStroke.size) {
            val point1 = currentStroke[i - 1]
            val point2 = currentStroke[i]

            val velocity = calculateVelocity(point1, point2)
            val direction = calculateDirection(point1, point2)
            val acceleration = if (strokeMetrics.isNotEmpty()) {
                calculateAcceleration(
                    strokeMetrics.last().velocity,
                    velocity,
                    (point2.timestamp - point1.timestamp) / 1000f
                )
            } else 0f

            strokeMetrics.add(
                StrokeMetric(
                    timestamp = point2.timestamp,
                    x = point2.x,
                    y = point2.y,
                    velocity = velocity,
                    acceleration = acceleration,
                    direction = direction,
                    pressure = point2.pressure,
                    strokeNumber = currentStrokeNumber
                )
            )
        }
        currentStroke.clear()
    }



    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            STORAGE_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Storage permissions granted", Toast.LENGTH_SHORT).show()
                saveSignature()
            } else {
                Toast.makeText(this,
                    "Storage permissions required to save signature",
                    Toast.LENGTH_LONG).show()
            }
        }
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
        if (!checkPermissions()) {
            Toast.makeText(this, "Storage permissions required", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = signaturePad.signatureBitmap

        try {
            // Create directory if it doesn't exist
            val directory = getExternalFilesDir(null)
            directory?.mkdirs()

            // Save signature image
            val imageFile = File(directory, "$currentUserName-signature-$attempNumber.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, outputStream)
            }

            // Save metrics
            val metricsFile = File(directory, "$currentUserName-signature-metrics-$attempNumber.csv")
            metricsFile.bufferedWriter().use { writer ->
                writer.write("Timestamp,X,Y,Velocity,Acceleration,Direction,Pressure,StrokeNumber\n")

                strokeMetrics.forEach { metric ->
                    writer.write("""
                        ${metric.timestamp},
                        ${metric.x},
                        ${metric.y},
                        ${metric.velocity},
                        ${metric.acceleration},
                        ${metric.direction ?: ""},
                        ${metric.pressure},
                        ${metric.strokeNumber}
                    """.trimIndent().replace("\n", "") + "\n")
                }
            }

            Toast.makeText(this,
                "Saved:\n${imageFile.absolutePath}\n${metricsFile.absolutePath}",
                Toast.LENGTH_LONG).show()
            Log.d("FileSave", "Signature saved at: ${imageFile.absolutePath}")
            Log.d("FileSave", "Metrics saved at: ${metricsFile.absolutePath}")
        } catch (e: IOException) {
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("FileSave", "Error saving files", e)
        }
    }

}
