package me.jonas.lmdx

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import kotlinx.coroutines.launch
import me.jonas.lmdx.data.Info

data class MangaView(val id: String, val title: String, val imageData: ByteArray?)

class MangaAdapter(
    private var mangaList: List<MangaView>,
    private val onItemClick: (MangaView) -> Unit // Item click listener
) : RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {

    inner class MangaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mangaTitle: TextView = itemView.findViewById(R.id.mangaTitle)
        private val mangaImage: ImageView = itemView.findViewById(R.id.mangaImage)

        fun bind(manga: MangaView) {
            mangaTitle.text = manga.title.trim('"')
            // Load image using BitmapFactory
            if(manga.imageData != null){
                val bitmap = BitmapFactory.decodeByteArray(manga.imageData, 0, manga.imageData.size)
                mangaImage.setImageBitmap(bitmap)
            }
            itemView.setOnClickListener {
                onItemClick.invoke(manga) // Invoke the item click listener
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.manga_library_items, parent, false)
        return MangaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        holder.bind(mangaList[position])
    }

    override fun getItemCount(): Int {
        return mangaList.size
    }
}

class LibraryActivity : AppCompatActivity() {
    private lateinit var database: MangaDatabase
    private lateinit var connection: MangaDexConnection


    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MangaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NavigationDrawer)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.library)

        database = Room.databaseBuilder(
            this.applicationContext,
            MangaDatabase::class.java,
            "manga"
        ).build()

        connection = MangaDexConnection(database)

        recyclerView = findViewById(R.id.recyclerview)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Sample manga data (replace with your actual data retrieval mechanism)
        val mangaList: MutableList<MangaView> = mutableListOf()

        lifecycleScope.launch  {
            for(info: Info in database.infoDao.getInfoInDb()){
                mangaList.add(MangaView(info.identifier, info.name, info.cover))
            }
            adapter = MangaAdapter(mangaList) {
                val intent = Intent(this@LibraryActivity, MangaActivity::class.java)
                intent.putExtra("manga_id", it.id)
                startActivity(intent)
            }
            recyclerView.adapter = adapter
        }
    }
}