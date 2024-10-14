package me.jonas.lmdx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_info")
data class ChapterInfo (
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "json_data") val jsonData: String,
)