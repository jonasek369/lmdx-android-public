package me.jonas.lmdx.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE identifier=:chapterIdentifier")
    suspend fun getManga(chapterIdentifier: String): List<Manga>

    @Query("SELECT page FROM manga WHERE identifier=:chapterIdentifier")
    suspend fun getPages(chapterIdentifier: String): List<Int>

    @Query("SELECT info.* FROM manga JOIN info ON manga.info_identifier = info.info_identifier WHERE manga.identifier = :chapterIdentifier LIMIT 1")
    suspend fun getInfoFromChapter(chapterIdentifier: String): Info?

    @Query("SELECT identifier FROM manga")
    suspend fun getChapterIds(): List<String>

    @Query("SELECT COUNT(*) > 0 AS row_found FROM manga WHERE identifier = :chapterIdentifier")
    suspend fun containsChapter(chapterIdentifier: String?): Boolean

    @Insert()
    suspend fun setManga(manga: Manga)

    @Delete
    suspend fun delete(manga: Manga)
}