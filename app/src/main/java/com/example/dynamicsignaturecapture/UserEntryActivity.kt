package com.example.dynamicsignaturecapture

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class UserEntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_entry)

        val usernameInput = findViewById<EditText>(R.id.username_input)
        val proceedButton = findViewById<Button>(R.id.proceed_button)

        proceedButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            if (username.isNotEmpty()) {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("USERNAME", username)
                startActivity(intent)
            } else {
                usernameInput.error = "Please enter a username"
            }
        }
    }
}