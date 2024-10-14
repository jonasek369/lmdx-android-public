package me.jonas.lmdx

import android.opengl.Visibility
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import kotlinx.coroutines.runBlocking


class DownloaderAdapter(
    var downloadsList: MutableList<String>,
    private val database: MangaDatabase,
    private val downloader: DownloadManager,
    var removeStopButton: MutableList<String>
) : RecyclerView.Adapter<DownloaderAdapter.DownloaderViewHolder>() {

    inner class DownloaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mangaTitle: TextView = itemView.findViewById(R.id.mangaTitle)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.downloadProgress)
        private val stopButton: Button = itemView.findViewById(R.id.stopButton)
        private val percentageTextView: TextView = itemView.findViewById(R.id.percentageTextView)
        private val removeButton: Button = itemView.findViewById(R.id.removeButton)

        fun bind(mangaIdentifier: String) {
            println("binding item")
            runBlocking {
                mangaTitle.text = database.infoDao.getTitle(mangaIdentifier)?.trim('"') ?: "Unknown title"
            }
            progressBar.max = 100
            downloader.uiElements[mangaIdentifier] = Pair(progressBar, percentageTextView)
            if(removeStopButton.contains(mangaIdentifier)){
                progressBar.progress = 100
                percentageTextView.text = "Finished"

                stopButton.visibility = View.GONE
            }

            stopButton.setOnClickListener {
                downloader.uiElements.remove(mangaIdentifier)
                downloader.removeDownload(mangaIdentifier)
                removeItem(downloadsList.indexOf(mangaIdentifier))
            }

            removeButton.setOnClickListener {
                downloader.uiElements.remove(mangaIdentifier)
                downloader.removeDownload(mangaIdentifier)
                removeStopButton.removeAt(removeStopButton.indexOf(mangaIdentifier))
                removeItem(downloadsList.indexOf(mangaIdentifier))
            }


        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloaderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.downloader_manga_items, parent, false)
        return DownloaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloaderViewHolder, position: Int) {
        holder.bind(downloadsList[position])
    }

    override fun getItemCount(): Int {
        return downloadsList.size
    }
    fun removeItem(position: Int) {
        if (position != RecyclerView.NO_POSITION && position < downloadsList.size) {
            downloadsList.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun removeItem(mangaIdentifier: String) {
        println("remove item $mangaIdentifier")
        val position =downloadsList.indexOf(mangaIdentifier)
        if (position != RecyclerView.NO_POSITION && position < downloadsList.size) {
            downloadsList.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun onFinish(mangaIdentifier: String) {
        removeStopButton.add(mangaIdentifier)
        notifyItemChanged(downloadsList.indexOf(mangaIdentifier))
    }

}


class DownloaderActivity : AppCompatActivity() {
    private lateinit var database: MangaDatabase
    private lateinit var connection: MangaDexConnection
    private lateinit var downloader: DownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NavigationDrawer)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.downloader)


        database = Room.databaseBuilder(
            this.applicationContext,
            MangaDatabase::class.java,
            "manga"
        ).build()
        connection = MangaDexConnection(database)
        downloader = DownloadManager.getInstance(database)

        val recyclerView: RecyclerView = findViewById(R.id.downloaderRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)


        val identifiers: MutableList<String> = mutableListOf()
        for(job in downloader.jobs){
            identifiers.add(job.first)
        }
        println("adding $identifiers")
        val recyclerAdapter = DownloaderAdapter(identifiers, database, downloader, mutableListOf())
        recyclerView.adapter = recyclerAdapter
        downloader.setAdapter(recyclerAdapter)
    }

    override fun onStop() {
        downloader.uiElements.clear()
        super.onStop()
    }
}
