package com.example.dynamicsignaturecapture

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class UserEntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_entry)

        val usernameInput = findViewById<EditText>(R.id.username_input)
        val proceedButton = findViewById<Button>(R.id.proceed_button)

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup2)



        proceedButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val selectedType = radioGroup.checkedRadioButtonId == R.id.radio_genuine
            if (username.isNotEmpty()) {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("USERNAME", username)
                intent.putExtra("SELECTED_TYPE", !selectedType)
                startActivity(intent)
            } else {
                usernameInput.error = "Please enter a username"
            }
        }
    }
}