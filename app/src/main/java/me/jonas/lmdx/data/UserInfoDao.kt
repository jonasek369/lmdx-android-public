package me.jonas.lmdx.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UserInfoDao {
    @Query("SELECT * FROM user_info LIMIT 1")
    suspend fun getUserInfo(): UserInfo?

    @Query("SELECT read_manga FROM user_info LIMIT 1")
    suspend fun getReadManga(): String?

    @Query("UPDATE user_info SET read_manga=:readManga, settings=:settings WHERE id=:id")
    suspend fun setNewData(id: Int, readManga: String?, settings: String?)

    @Query("UPDATE user_info SET settings=:settings WHERE id=:id")
    suspend fun setSettings(id: Int, settings: String?)

    @Query("UPDATE user_info SET read_manga=:readManga WHERE id=:id")
    suspend fun setReadManga(id: Int, readManga: String?)

    @Upsert()
    suspend fun setUserInfo(userInfo: UserInfo)

    @Delete
    suspend fun delete(userInfo: UserInfo)
}