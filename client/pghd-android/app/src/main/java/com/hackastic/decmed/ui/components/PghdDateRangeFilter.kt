package com.hackastic.decmed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PghdDateRangeFilter(
    startDateMillis: Long?,
    endDateMillis: Long?,
    onDateRangeChange: (Long?, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var pickerTarget by rememberSaveable { mutableStateOf<DatePickerTarget?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { pickerTarget = DatePickerTarget.Start }
            ) {
                Text(startDateMillis?.let { dateFormatter.format(Date(it)) } ?: "Start Date")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { pickerTarget = DatePickerTarget.End }
            ) {
                Text(endDateMillis?.let { dateFormatter.format(Date(it)) } ?: "End Date")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = startDateMillis != null || endDateMillis != null,
                onClick = { onDateRangeChange(null, null) }
            ) {
                Text("Clear Date Filter")
            }
        }
    }

    pickerTarget?.let { target ->
        val initialMillis = when (target) {
            DatePickerTarget.Start -> startDateMillis
            DatePickerTarget.End -> endDateMillis
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { pickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            val localDate = selectedMillis.toLocalDateFromPicker()
                            when (target) {
                                DatePickerTarget.Start -> {
                                    onDateRangeChange(localDate.startOfDayMillis(), endDateMillis)
                                }
                                DatePickerTarget.End -> {
                                    onDateRangeChange(startDateMillis, localDate.endOfDayMillis())
                                }
                            }
                        }
                        pickerTarget = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private enum class DatePickerTarget {
    Start,
    End
}

private fun Long.toLocalDateFromPicker(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun LocalDate.endOfDayMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
