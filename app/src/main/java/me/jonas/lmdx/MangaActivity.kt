package me.jonas.lmdx

import android.content.Intent
import android.graphics.BitmapFactory
import android.opengl.Visibility
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jonas.lmdx.data.ChapterInfo
import me.jonas.lmdx.data.ReadInfo

data class Chapter(
    val identifier: String,
    val name: String?,
    val chapterNumber: Float,
    val chapterVolume: Float?,
    val prevChapter: String?,
    val nextChapter: String?,
    val viewed: Boolean,
    val pages: Int
)


class ChapterAdapter(
    var chapterList: MutableList<Chapter>,
    private val onItemClick: (Chapter) -> Unit, // Item click listener
    private val onViewedClick: (Chapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    inner class ChapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mangaSeen: ImageView = itemView.findViewById(R.id.mangaSeen)
        private val mangaTitle: TextView = itemView.findViewById(R.id.mangaTitle)
        private val mangaChapterNumber: TextView = itemView.findViewById(R.id.mangaChapterNumber)
        private val mangaSeparator: TextView = itemView.findViewById(R.id.mangaSeparator)


        fun bind(chapter: Chapter) {
            if(chapter.viewed){
                mangaSeen.setImageResource(R.drawable.outline_remove_red_eye_24)
            }
            else{
                mangaSeen.setImageResource(R.drawable.baseline_visibility_off_24)
            }

            if(chapter.name != null){
                mangaTitle.visibility = View.VISIBLE
                mangaSeparator.visibility = View.VISIBLE
                mangaTitle.text = chapter.name
            }else{
                mangaTitle.visibility = View.GONE
                mangaSeparator.visibility = View.GONE
            }
            mangaChapterNumber.text = "Ch. ${chapter.chapterNumber.toString()}"

            itemView.setOnClickListener{
                onItemClick.invoke(chapter)
            }

            mangaSeen.setOnClickListener {
                onViewedClick.invoke(chapter)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.manga_chapter_items, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapterList[position])
    }

    override fun getItemCount(): Int {
        return chapterList.size
    }

    fun removeItem(position: Int) {
        if (position != RecyclerView.NO_POSITION && position < chapterList.size) {
            chapterList.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun reverseItems() {
        chapterList.reverse()
        notifyDataSetChanged()
    }

}


class MangaActivity : AppCompatActivity() {
    private lateinit var database: MangaDatabase
    private lateinit var connection: MangaDexConnection

    private lateinit var downloader: DownloadManager


    suspend fun getReadInfo(mangaId: String): List<String>?{
        return database.readInfoDao.getReadInfo(mangaId)?.let {
            Json.parseToJsonElement(it.chaptersReadJson).jsonArray.toList().map {
                    jsonElement -> jsonElementToString(jsonElement)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NavigationDrawer)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.manga)


        database = Room.databaseBuilder(
            this.applicationContext,
            MangaDatabase::class.java,
            "manga"
        ).build()

        downloader = DownloadManager.getInstance(database)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerviewChapters)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Retrieve the manga_id value from the Intent
        val mangaId = intent.getStringExtra("manga_id")?: return
        println("mangaId: $mangaId")

        val reverseButton: Button = findViewById(R.id.reverseButton)

        val button: Button = findViewById(R.id.downloadButton)
        println("load data")

        button.setOnClickListener(){
            for(job in downloader.jobs){
                if(job.first == mangaId){
                    return@setOnClickListener
                }
            }
            downloader.downloadManga(mangaId)
            button.visibility = View.INVISIBLE
        }

        println("set onclick")
        try{
            connection = MangaDexConnection(database)
        }catch (e: Exception){
            println("error making connection")
            e.printStackTrace()
        }

        lifecycleScope.launch  {
            var info = database.infoDao.getInfo(mangaId)
            if(info == null){
                println("db info is null getting API info")
                info = toInfoFromObject(connection.getMangaInfo(mangaId))
            }

            val titleFl: TextView = findViewById(R.id.mangaTitleFl)
            titleFl.text = info.name.trim('"')

            val titleEn: TextView = findViewById(R.id.mangaTitleEn)
            titleEn.text = info.name.trim('"')

            if(info.cover != null){
                val cover: ImageView = findViewById(R.id.mangaCover)
                val bitmap = BitmapFactory.decodeByteArray(info.cover, 0, info.cover!!.size)
                cover.setImageBitmap(bitmap)
            }

            val description: TextView = findViewById(R.id.mangaDescription)
            val descriptionText = info.description?.trim('"')
            val markwon = Markwon.create(this@MangaActivity)
            if(descriptionText != null){
                markwon.setMarkdown(description, descriptionText)
            }

            var chapterInfo: ChapterInfo;
            try{
                // try to get current chapterinfo from api and save it to database as the newest one
                chapterInfo = ChapterInfo(mangaId, connection.getChapterList(mangaId).toString())
                database.chapterInfoDao.setChapterInfo(chapterInfo)
            }catch (e: Exception){
                // if it fails get from database
                chapterInfo = database.chapterInfoDao.getChapterInfo(mangaId)?: ChapterInfo(mangaId, "[]")
                // should always be something
            }

            val dbChapters: List<String> = database.mangaDao.getChapterIds()
            val chapters: MutableList<Chapter> = mutableListOf()
            var prevChapterId: String? = null
            var nextChapterId: String? = null
            val chaptersJson = Json.parseToJsonElement(chapterInfo.jsonData).jsonArray

            val viewedChapters = getReadInfo(mangaId)

            for(chapter in chaptersJson.withIndex()){
                val chapterObject = chapter.value.jsonObject
                val chapterId = chapterObject["id"].toString().trim('"')
                if(chapterId !in dbChapters){
                    continue
                }
                nextChapterId = if (chapter.index + 1 < chaptersJson.size) {
                    chaptersJson[chapter.index + 1].jsonObject["id"].toString().trim('"')
                } else {
                    null
                }


                chapters.add(
                    Chapter(
                        identifier = chapterId,
                        name = chapterObject["attributes"]?.jsonObject?.get("title").toString().trim('"'),
                        chapterNumber= chapterObject["attributes"]?.jsonObject?.get("chapter").toString().trim('"').toFloat(),
                        chapterVolume= chapterObject["attributes"]?.jsonObject?.get("volume").toString().trim('"').toFloatOrNull(),
                        viewed = viewedChapters.let {
                            if(it.isNullOrEmpty())
                                false
                            else
                                it.contains(chapterId)
                        },
                        prevChapter = prevChapterId,
                        nextChapter = nextChapterId,
                        pages = chapterObject["attributes"]?.jsonObject?.get("pages").toString().trim('"').toInt()
                    )
                )
                prevChapterId = chapterId
            }
            chapters.sortBy { it.chapterNumber }
            chapters.reverse()

            val chapterAdapter: ChapterAdapter = ChapterAdapter(chapters, {
                val intentRedirect = Intent(this@MangaActivity, ChapterReader::class.java)
                intentRedirect.putExtra("chapter_id", it.identifier)
                intentRedirect.putExtra("manga_id", mangaId)

                intentRedirect.putExtra("chapter_number", it.chapterNumber)
                intentRedirect.putExtra("chapter_volume", it.chapterVolume)
                intentRedirect.putExtra("chapter_pages", it.pages)

                intentRedirect.putExtra("next_chapter_identifier", it.nextChapter)
                intentRedirect.putExtra("prev_chapter_identifier", it.prevChapter)


                startActivity(intentRedirect)
            }, {
                CoroutineScope(Dispatchers.IO).async {
                    val currentData = getReadInfo(mangaId)?.toMutableList() // Make a mutable copy
                    if (currentData != null) {
                        if (currentData.contains(it.identifier)) {
                            currentData.remove(it.identifier)
                        } else {
                            currentData.add(it.identifier)
                        }
                        database.readInfoDao.setReadInfo(ReadInfo(
                            mangaId,
                            Json.encodeToString(currentData)
                        ))
                    } else {
                        database.readInfoDao.setReadInfo(ReadInfo(
                            mangaId,
                            Json.encodeToString(listOf(it.identifier))
                        ))
                    }
                }
            })

            reverseButton.setOnClickListener {
                if(reverseButton.text == "Descending"){
                    reverseButton.text = "Ascending"
                }else {
                    reverseButton.text = "Descending"
                }

                chapterAdapter.reverseItems()

            }
            recyclerView.adapter = chapterAdapter
        }

    }
}