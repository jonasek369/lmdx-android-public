package me.jonas.lmdx

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.jonas.lmdx.data.ChapterInfo
import me.jonas.lmdx.data.Manga
import java.security.AccessController.getContext


fun getScreenDimensions(): Pair<Int, Int> {
    return Pair<Int, Int>(
        Resources.getSystem().displayMetrics.widthPixels,
        Resources.getSystem().displayMetrics.heightPixels
    );
}

class ChapterReader : AppCompatActivity() {
    private lateinit var database: MangaDatabase
    private lateinit var connection: MangaDexConnection

    private var nextChapter: String? = null
    private var prevChapter: String? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val intentRedirect = Intent(this@ChapterReader, MangaActivity::class.java)
        intentRedirect.putExtra("manga_id", intent.getStringExtra("manga_id"))
        startActivity(intentRedirect)
        overridePendingTransition(android.R.anim.accelerate_interpolator, android.R.anim.accelerate_decelerate_interpolator);
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NavigationDrawer)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.chapter_reader)

        database = Room.databaseBuilder(
            this.applicationContext,
            MangaDatabase::class.java,
            "manga"
        ).build()
        connection = MangaDexConnection(database)

        val (screenWidth, _) = getScreenDimensions()

        val chapterId = intent.getStringExtra("chapter_id") ?: return
        val mangaId = intent.getStringExtra("manga_id") ?: return
        var totalPages = intent.getIntExtra("chapter_pages", -1)
        var atPage = intent.getIntExtra("at_page", 1)



        lifecycleScope.launch {

            val backButton: Button = findViewById(R.id.backButton)
            backButton.setOnClickListener {
                /*
                val intentRedirect = Intent(this@ChapterReader, MangaActivity::class.java)
                intentRedirect.putExtra("manga_id", mangaId)
                startActivity(intentRedirect)
                overridePendingTransition(android.R.anim.accelerate_interpolator, android.R.anim.accelerate_decelerate_interpolator);*/
                super.onBackPressed()
            }

            if(totalPages == -1)
                totalPages = database.recordDao.getRecord(chapterId)?.pages!!

            if(atPage == -1){
                atPage = totalPages
            }

            val mangas: List<Manga> = database.mangaDao.getManga(chapterId)

            if(totalPages != mangas.size){
                throw Exception("total pages arent same as mangas.size!! $chapterId")
            }

            val chapterData = connection.getNextAndPrev(chapterId, mangaId)
            if(chapterData?.get("next") != null){
                nextChapter = jsonElementToString(chapterData["next"]?.jsonObject?.get("id"))
            }
            if(chapterData?.get("prev") != null){
                prevChapter = jsonElementToString(chapterData["prev"]?.jsonObject?.get("id"))
            }
            println("next $nextChapter prev $prevChapter")

            val pageText: TextView = findViewById(R.id.pageText)
            pageText.text = "Pg.  $atPage/${totalPages}"
            val chapterText: TextView = findViewById(R.id.chapterText)
            if(chapterData?.get("current") != null){
                val chapter = jsonElementToString(chapterData["current"]?.jsonObject?.get("chapter"))
                val volume = jsonElementToString(chapterData["current"]?.jsonObject?.get("volume"))
                if(volume != "null"){
                    chapterText.text = "Vol. ${volume} Ch. ${chapter}"
                }
                else{
                    chapterText.text = "Ch. ${chapter}"
                }
            }
            val chapterImage: ImageView = findViewById(R.id.chapterImage)

            val bitmap = mangas[atPage-1].data.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
            if (bitmap != null)
                chapterImage.setImageBitmap(bitmap)

            chapterImage.setOnTouchListener { v, event ->
                val x = event.x // X coordinate of the touch event
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if(x < screenWidth/2){
                            if(atPage >= 2){
                                atPage--
                            }else if(prevChapter != null){
                                val intentRedirect = Intent(this@ChapterReader, ChapterReader::class.java)
                                intentRedirect.putExtra("chapter_id", prevChapter)
                                intentRedirect.putExtra("manga_id", mangaId)
                                intentRedirect.putExtra("at_page", -1)
                                startActivity(intentRedirect)
                                overridePendingTransition(android.R.anim.accelerate_interpolator, android.R.anim.accelerate_decelerate_interpolator);
                            }
                        }else{
                            if(atPage < totalPages){
                                atPage++
                            }else if(nextChapter != null){
                                val intentRedirect = Intent(this@ChapterReader, ChapterReader::class.java)
                                intentRedirect.putExtra("chapter_id", nextChapter)
                                intentRedirect.putExtra("manga_id", mangaId)
                                startActivity(intentRedirect)
                                overridePendingTransition(android.R.anim.accelerate_interpolator, android.R.anim.accelerate_decelerate_interpolator);
                            }
                        }
                        println("set new image $atPage index ${atPage-1}")
                        val bitmapClick =
                            mangas[atPage-1].data.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        if(bitmapClick != null)
                            chapterImage.setImageBitmap(bitmapClick)
                        pageText.text = "Pg.  $atPage/${totalPages}"
                        v.performClick()
                        true
                    }
                    else -> false
                }
            }

        }
    }
}