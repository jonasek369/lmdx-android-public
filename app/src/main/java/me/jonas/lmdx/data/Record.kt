package me.jonas.lmdx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "record")
data class Record (
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "pages") val pages: Int
)