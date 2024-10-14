package me.jonas.lmdx.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RecordDao {
    @Query("SELECT * FROM record WHERE identifier=:identifier LIMIT 1")
    suspend fun getRecord(identifier: String): Record?

    @Upsert
    suspend fun setRecord(record: Record)

    @Delete
    suspend fun delete(record: Record)
}