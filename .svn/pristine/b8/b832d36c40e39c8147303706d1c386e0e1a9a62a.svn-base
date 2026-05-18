package com.softland.callqtv.data.repository

import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.local.TokenRecordEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TokenRecordRepository(private val database: AppDatabase) {

    private val dao = database.tokenRecordDao()

    suspend fun saveRecord(
        macAddress: String,
        counterId: Int,
        counterName: String,
        tokenNumber: String,
        keypadSerialNumber: String,
        calledTime: String // This is the time provided by MQTT payload
    ) {
        val now = LocalDate.now()
        val record = TokenRecordEntity(
            macAddress = macAddress,
            counterId = counterId,
            counterName = counterName,
            tokenNumber = tokenNumber,
            keypadSerialNumber = keypadSerialNumber,
            createdDate = now.toString(),
            createdTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            calledTime = calledTime,
            isUploaded = 0
        )
        dao.insert(record)
    }

    suspend fun performDailyCleanup() {
        val today = LocalDate.now().toString()
        dao.deleteOldRecords(today)
    }

    suspend fun getAllRecords() = dao.getAllRecords()
}
