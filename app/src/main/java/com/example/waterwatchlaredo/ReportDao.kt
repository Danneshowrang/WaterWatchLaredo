package com.example.waterwatchlaredo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReportDao {
    @Insert
    suspend fun insert(report: Report)

    // Reuse fixed sample IDs and refresh their fictional timestamps for the next demo.
    @Upsert
    suspend fun insertSampleReports(reports: List<Report>)

    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    suspend fun getAll(): List<Report>

    @Query("SELECT * FROM reports WHERE timestamp > :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getNewerThan(sinceTimestamp: Long): List<Report>
}
