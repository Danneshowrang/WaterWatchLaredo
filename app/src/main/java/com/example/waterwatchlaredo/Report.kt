package com.example.waterwatchlaredo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val problemType: String,
    val note: String = "",
    val latitude: Double,
    val longitude: Double,
    // Milliseconds since January 1, 1970, UTC.
    val timestamp: Long,
    // Keep sample reports identifiable after saving and restarting the app.
    @ColumnInfo(defaultValue = "0")
    val isSample: Boolean = false
)
