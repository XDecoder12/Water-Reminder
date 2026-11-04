package com.example.water

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupManager {

    suspend fun exportBackup(context: Context, dao: WaterDao, uri: Uri): Boolean {
        return try {
            val logs = dao.getAllLogs()
            val jsonArray = JSONArray()

            for (log in logs) {
                val jsonObject = JSONObject().apply {
                    put("date", log.date)
                    put("amountDrankMl", log.amountDrankMl)
                    put("dailyGoalMl", log.dailyGoalMl)
                    put("goalReached", log.goalReached)
                }
                jsonArray.put(jsonObject)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonArray.toString(4)) // Indented JSON formatting
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackup(context: Context, dao: WaterDao, uri: Uri): Boolean {
        return try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val jsonArray = JSONArray(stringBuilder.toString())
            val logsToRestore = mutableListOf<WaterLog>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val log = WaterLog(
                    date = obj.getString("date"),
                    amountDrankMl = obj.getInt("amountDrankMl"),
                    dailyGoalMl = obj.getInt("dailyGoalMl"),
                    goalReached = obj.getBoolean("goalReached")
                )
                logsToRestore.add(log)
            }

            dao.insertAll(logsToRestore)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}