package me.jonas.lmdx.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InfoDao {
    @Query("SELECT * FROM info i WHERE i.info_identifier IN (SELECT info_identifier FROM manga)")
    suspend fun getInfoInDb(): List<Info>

    @Query("SELECT * FROM info WHERE identifier=:mangaIdentifier LIMIT 1")
    suspend fun getInfo(mangaIdentifier: String): Info?

    @Query("SELECT info_identifier FROM info WHERE identifier=:mangaIdentifier LIMIT 1")
    suspend fun getInfoIdentifier(mangaIdentifier: String): Int?

    @Query("SELECT name FROM info WHERE identifier=:mangaIdentifier LIMIT 1")
    suspend fun getTitle(mangaIdentifier: String): String?

    @Query("SELECT * FROM info")
    suspend fun getAllInfo(): List<Info>

    @Upsert
    suspend fun setInfo(info: Info)

    @Delete
    suspend fun delete(info: Info)
}