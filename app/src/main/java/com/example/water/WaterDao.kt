package com.example.water

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WaterDao {
    // Saves a new day's log, or updates it if they drink more water
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: WaterLog)

    // Gets the water record for a specific day
    @Query("SELECT * FROM water_logs WHERE date = :targetDate")
    suspend fun getLogByDate(targetDate: String): WaterLog?

    // Gets all records so we can light up the calendar!
    @Query("SELECT * FROM water_logs")
    suspend fun getAllLogs(): List<WaterLog>
}