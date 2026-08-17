package com.example.water

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey
    val date: String,          // We will save dates like "2026-08-14"
    val amountDrankMl: Int,    // How much they drank that day
    val dailyGoalMl: Int,      // What their goal was that day
    val goalReached: Boolean   // Did they hit the streak?
)