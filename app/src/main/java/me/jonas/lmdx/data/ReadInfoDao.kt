package me.jonas.lmdx.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadInfoDao {
    @Query("SELECT * FROM read_info WHERE identifier=:identifier LIMIT 1")
    suspend fun getReadInfo(identifier: String): ReadInfo?

    @Upsert
    suspend fun setReadInfo(readInfo: ReadInfo)

    @Delete
    suspend fun delete(record: ReadInfo)
}