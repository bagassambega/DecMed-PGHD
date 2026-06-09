package com.hackastic.decmed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackastic.decmed.ui.theme.DecMedTheme
import com.hackastic.decmed.utils.DecmedLog

class HealthConnectPermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DecmedLog.i(TAG, "Opened Health Connect permission rationale: action=${intent?.action}")

        setContent {
            DecMedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HealthConnectPermissionContent(onDone = ::finish)
                }
            }
        }
    }

    private companion object {
        const val TAG = "HealthConnectPermission"
    }
}

@Composable
private fun HealthConnectPermissionContent(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DecMed Health Connect Access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "DecMed PGHD reads Health Connect records that are relevant for patient-generated health data, including activity, steps, heart rate, oxygen saturation, respiratory rate, sleep, distance, calories, and selected wearable summaries."
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "The data is stored locally first so the patient can review it. Data is only submitted to DecMed PGHD after the patient starts collection and chooses to sync or submit the collected records."
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "DecMed uses this data for PGHD collection, synchronization status, and controlled sharing with authorized healthcare personnel."
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to DecMed")
        }
    }
}
