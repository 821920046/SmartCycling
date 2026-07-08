package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.data.RideEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 骑行历史列表。 */
@Composable
fun HistoryScreen(rides: List<RideEntity>, modifier: Modifier = Modifier) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp)) {
        items(rides, key = { it.id }) { ride ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(fmt.format(Date(ride.startedAt)), fontSize = 14.sp)
                Text(
                    "%.1f km · %s · 均速 %.1f km/h".format(
                        ride.distanceKm,
                        formatDuration(ride.durationSec),
                        ride.avgSpeedKmh,
                    ),
                    fontSize = 16.sp,
                )
            }
            HorizontalDivider()
        }
    }
}

private fun formatDuration(sec: Long): String =
    "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
