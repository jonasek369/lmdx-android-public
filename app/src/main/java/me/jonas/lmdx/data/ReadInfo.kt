package me.jonas.lmdx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_info")
data class ReadInfo (
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "chapters_read_json") val chaptersReadJson: String,
)