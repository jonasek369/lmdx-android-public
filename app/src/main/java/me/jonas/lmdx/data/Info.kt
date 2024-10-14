package me.jonas.lmdx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "info")
data class Info (
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name="info_identifier") val infoIdentifier: Int=0,
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "cover") val cover: ByteArray?,
    @ColumnInfo(name = "small_cover") val smallCover: ByteArray?,
    @ColumnInfo(name = "manga_format") val mangaFormat: String?,
    @ColumnInfo(name = "manga_genre") val mangaGenre: String?,
    @ColumnInfo(name = "content_rating") val contentRating: String?
)