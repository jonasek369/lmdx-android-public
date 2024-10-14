package me.jonas.lmdx.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ChapterInfoDao {
    @Query("SELECT * FROM chapter_info WHERE identifier=:identifier LIMIT 1")
    suspend fun getChapterInfo(identifier: String): ChapterInfo?

    @Upsert
    suspend fun setChapterInfo(info: ChapterInfo)

    @Delete
    suspend fun delete(info: ChapterInfo)
}