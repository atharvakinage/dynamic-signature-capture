package com.example.dynamicsignaturecapture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
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
    val pressure: Float? = null
)

data class StrokeMetric(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val velocity: Float,
    val acceleration: Float,
    val direction: Float?,
    val pressure: Float?,
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signaturePad = findViewById(R.id.signature_pad)

        val clearButton: Button = findViewById(R.id.clear_button)
        val saveButton: Button = findViewById(R.id.save_button)

        clearButton.setOnClickListener { signaturePad.clear() }
        saveButton.setOnClickListener {
            if (checkPermissions()) {
                saveSignature()
            } else {
                requestPermissions()
            }
        }

        signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {
                currentStroke.clear()
            }

            override fun onSigned() {
                // Capture stroke points and calculate metrics
                val points = signaturePad.points
                val timestamp = System.currentTimeMillis()

                if (points.isNotEmpty()) {
                    for (i in 1 until points.size) {
                        val point1 = StrokePoint(
                            x = points[i - 1].x,
                            y = points[i - 1].y,
                            timestamp = timestamp + i * 10 // Simulated time difference
                        )
                        val point2 = StrokePoint(
                            x = points[i].x,
                            y = points[i].y,
                            timestamp = timestamp + (i + 1) * 10
                        )
                        val velocity = calculateVelocity(point1, point2)
                        val direction = calculateDirection(point1, point2)
                        val acceleration = if (i > 1) calculateAcceleration(
                            strokeMetrics.last().velocity,
                            velocity,
                            10 / 1000f
                        ) else 0f

                        strokeMetrics.add(
                            StrokeMetric(
                                timestamp = point2.timestamp,
                                x = point2.x,
                                y = point2.y,
                                velocity = velocity,
                                acceleration = acceleration,
                                direction = direction,
                                pressure = null,
                                strokeNumber = 1
                            )
                        )
                    }
                }
            }

            override fun onClear() {
                strokeMetrics.clear()
            }
        })
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
            val imageFile = File(directory, "signature_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, outputStream)
            }

            // Save metrics
            val metricsFile = File(directory, "signature_metrics_${System.currentTimeMillis()}.csv")
            metricsFile.bufferedWriter().use { writer ->
                // Write CSV header
                writer.write("Timestamp,X,Y,Velocity,Acceleration,Direction,Pressure,StrokeNumber\n")

                // Write data rows
                strokeMetrics.forEach { metric ->
                    writer.write("""
                        ${metric.timestamp},
                        ${metric.x},
                        ${metric.y},
                        ${metric.velocity},
                        ${metric.acceleration},
                        ${metric.direction ?: ""},
                        ${metric.pressure ?: ""},
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
