package me.jonas.lmdx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_info")
data class UserInfo (
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val identifier: Int = 0,
    @ColumnInfo(name = "read_manga") val readManga: String,
    @ColumnInfo(name = "settings") val settings: String
)