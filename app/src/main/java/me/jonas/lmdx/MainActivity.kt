package me.jonas.lmdx

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.CursorWindow
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import kotlinx.coroutines.*
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import me.jonas.lmdx.data.Info
import me.jonas.lmdx.data.UserInfo
import kotlin.time.TimeSource

suspend fun fetchSearchMangaInfo(database: MangaDatabase, connection: MangaDexConnection, manga: MangaJson): MangaView {
    val dbInfo = database.infoDao.getInfo(manga.id)
    return if (dbInfo == null) {
        val mangaInfo = toInfoFromObject(connection.getMangaInfo(manga.id))
        database.infoDao.setInfo(mangaInfo)
        MangaView(mangaInfo.identifier, mangaInfo.name, mangaInfo.cover)
    } else {
        MangaView(dbInfo.identifier, dbInfo.name, dbInfo.cover)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var database: MangaDatabase
    private lateinit var connection: MangaDexConnection

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var searchView: SearchView



    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NavigationDrawer)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true;
            field.set(null, 16 * 1024 * 1024); //sets 16MB cursor
        } catch (e: Exception) {
            e.printStackTrace()
        }

        database = Room.databaseBuilder(
            this.applicationContext,
            MangaDatabase::class.java,
            "manga"
        ).build()



        connection = MangaDexConnection(database)

        drawerLayout = findViewById(R.id.drawer)

        searchView = findViewById(R.id.search_view)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerview)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val searchView: SearchView = findViewById(R.id.search_view)

        val navigationView: NavigationView = findViewById(R.id.navigation_view)

        actionBarDrawerToggle = ActionBarDrawerToggle(this,drawerLayout,R.string.Open,R.string.Close)
        // add a drawer listener into  drawer layout
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()

        // show menu icon and back icon while drawer open
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            // Handle item click here
            when (menuItem.itemId) {
                R.id.profile_nav_menu -> {
                    true
                }
                R.id.library_nav_menu -> {
                    val intent = Intent(this@MainActivity, LibraryActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.downloader_nav_menu -> {
                    val intent = Intent(this@MainActivity, DownloaderActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.updates_nav_menu -> {
                    println("nav updates")
                    true
                }
                else -> false
            }
        }
        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if(query.isNullOrEmpty())
                    return false

                val job = lifecycleScope.launch  {
                    val mangas = connection.searchManga(query, 5) ?: return@launch

                    val mangaViewsDeferred = mangas.map { manga ->
                        async(Dispatchers.IO) {
                            fetchSearchMangaInfo(database, connection, manga)
                        }
                    }

                    val mangaList = mangaViewsDeferred.awaitAll()

                    recyclerView.adapter = MangaAdapter(mangaList) {
                        val intent = Intent(this@MainActivity, MangaActivity::class.java)
                        intent.putExtra("manga_id", it.id)
                        startActivity(intent)
                    }
                    recyclerView.visibility = View.VISIBLE
                }

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    recyclerView.visibility = View.INVISIBLE
                }
                return false
            }
        })

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // check conndition for drawer item with menu item
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)){
            true
        }else{
            super.onOptionsItemSelected(item)
        }

    }
}