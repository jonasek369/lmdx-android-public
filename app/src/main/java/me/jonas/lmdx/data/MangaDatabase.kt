package me.jonas.lmdx

import android.content.Context
import android.os.Parcelable
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import me.jonas.lmdx.data.*

@Database(entities = [Info::class, Record::class, Manga::class, ChapterInfo::class, UserInfo::class, ReadInfo::class], version = 1, exportSchema = false)
abstract class MangaDatabase : RoomDatabase() {
    abstract val infoDao: InfoDao
    abstract val recordDao: RecordDao
    abstract val mangaDao: MangaDao
    abstract val chapterInfoDao: ChapterInfoDao
    abstract val userInfoDao: UserInfoDao
    abstract val readInfoDao: ReadInfoDao
}