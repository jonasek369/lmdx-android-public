package me.jonas.lmdx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "manga", foreignKeys = arrayOf(
    ForeignKey(entity = Info::class, parentColumns = arrayOf("info_identifier"), childColumns = arrayOf("info_identifier"))
))
data class Manga (
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name="rowId") val rowId: Int=0,
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "page") val page: Int,
    @ColumnInfo(name = "data") val data: ByteArray,
    @ColumnInfo(name = "info_identifier") val infoId: Int?

)