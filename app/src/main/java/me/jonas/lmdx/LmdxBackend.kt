package me.jonas.lmdx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ProgressBar
import android.widget.TextView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jonas.lmdx.data.ChapterInfo
import me.jonas.lmdx.data.Info
import me.jonas.lmdx.data.Manga
import me.jonas.lmdx.data.Record
import me.jonas.lmdx.data.UserInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.copyOf
import kotlin.collections.emptyList
import kotlin.collections.hashMapOf
import kotlin.collections.isNullOrEmpty
import kotlin.collections.iterator
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.mutableListOf
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.sortBy
import kotlin.collections.toList
import kotlin.collections.toMutableMap
import kotlin.collections.withIndex


@Serializable
data class MangaTags(
    val id: String,
    val type: String,
    val attributes: JsonObject,
    val relationships: JsonArray
)

@Serializable
data class MangaAttributes(
    val title: JsonObject,
    val altTitles: JsonArray,
    val description: JsonObject,
    val isLocked: Boolean,
    val links: JsonObject,
    val originalLanguage: String,
    val lastVolume: String?,
    val lastChapter: String?,
    val publicationDemographic: String?,
    val status: String,
    val year: Int?,
    val contentRating: String,
    val tags: List<MangaTags>,
    val state: String,
    val chapterNumbersResetOnNewVolume: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val version: Int?,
    val availableTranslatedLanguages: JsonArray,
    val latestUploadedChapter: String?
)

@Serializable
data class MangaJson(
    val id: String,
    val type: String,
    val attributes: MangaAttributes,
    val relationships: JsonArray
)

data class InfoObject(
    val identifier: String,
    val name: String,
    val description: String?,
    val cover: ByteArray?,
    val smallCover: ByteArray?,
    val mangaFormat: String?,
    val mangaGenre: String?,
    val contentRating: String?
)

fun toInfoFromObject(info: InfoObject): Info {
    return Info(
        identifier = info.identifier,
        name = info.name,
        description = info.description,
        cover = info.cover,
        smallCover = info.smallCover,
        contentRating = info.contentRating,
        mangaFormat = info.mangaFormat,
        mangaGenre = info.mangaGenre,
    )
}


fun jsonElementToString(element: JsonElement?): String {
    if (element == null) {
        return "null"
    }
    return element.toString().trim('"')
}

fun tryToGet(json: JsonElement, path: List<String>): JsonElement? {
    println(json)
    try {
        var currentJson: JsonObject? = null
        for (element in path.withIndex()) {
            if (currentJson == null) {
                if (element.index == path.size) {
                    return json.jsonObject[element.value]
                }
                currentJson = json.jsonObject[element.value]?.jsonObject
            } else {
                if (element.index == path.size) {
                    return json.jsonObject[element.value]
                }
                currentJson = currentJson[element.value]?.jsonObject
            }
        }
        return null
    } catch (e: Exception) {
        println("try get ${e.message}")
        return null
    }
}


class MangaDexConnection(mangaDb: MangaDatabase? = null) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }

    }
    private val API_URL = "https://api.mangadex.org";
    private val database = mangaDb

    private fun getDefaultRequestParameters(): JsonElement {
        // default parameters for every mangadex api request
        return Json.parseToJsonElement("""{"contentRating[]": ["safe", "suggestive", "erotic"]}""")
    }

    private fun getRequest(
        url: String,
        defaultParams: MutableMap<String, JsonElement>
    ): HttpResponse {
        // makeshift url builder that is made to support mangadex arrays
        return runBlocking {
            return@runBlocking httpClient.get(url) {
                contentType(ContentType.Application.Json)
                url {
                    for (a in defaultParams) {
                        try {
                            val objects = a.value.jsonArray
                            val stringList: MutableList<String> = mutableListOf()

                            for (obj in objects) {
                                stringList += obj.toString().trim('"')
                            }

                            parameters.appendAll(a.key, stringList)
                        } catch (e: IllegalArgumentException) {
                            parameters.append(a.key, a.value.toString().trim('"'))
                        }
                    }
                }
            }
        }
    }

    fun searchManga(
        mangaName: String,
        limit: Int = 1,
        includeTagIds: List<String>? = null,
        excludeTagIds: List<String>? = null
    ): List<MangaJson>? {
        return runBlocking {
            val defaultParams = getDefaultRequestParameters().jsonObject.toMutableMap();

            defaultParams["title"] = JsonPrimitive(mangaName);
            defaultParams["limit"] = JsonPrimitive(limit);

            if (includeTagIds != null) {
                defaultParams["includedTags[]"] = Json.encodeToJsonElement(includeTagIds)
            }
            if (excludeTagIds != null) {
                defaultParams["excludedTags[]"] = Json.encodeToJsonElement(excludeTagIds)
            }
            val response: HttpResponse;
            try {
                response = getRequest("$API_URL/manga", defaultParams)
            } catch (e: Exception) {
                e.printStackTrace()
                return@runBlocking null
            }
            println(response)
            val responseJson = Json.parseToJsonElement(response.body()).jsonObject["data"]
                ?: return@runBlocking null
            val mangas = Json.decodeFromJsonElement<List<MangaJson>>(responseJson);

            return@runBlocking mangas
        }
    }

    fun downloadMangaPages(
        chapterIdentifier: String,
        pagesInDatabase: List<Int> = listOf(),
        silent: Boolean = true,
        rateLimitCallback: ((Float) -> Boolean)? = null
    ): List<Pair<Int, ByteArray>>? {
        return runBlocking {
            val metadata = httpClient.get("$API_URL/at-home/server/$chapterIdentifier")
            val remainingRequests =
                metadata.headers["X-RateLimit-Remaining"]?.toInt()
                    ?: 1 // This should always be supplied by the api
            val retryAfter = metadata.headers["X-RateLimit-Retry-After"]?.toFloat() ?: 0 // <--|
            if (remainingRequests <= 0) {
                println("Rate limit reached")
                if (rateLimitCallback != null && !rateLimitCallback.invoke(retryAfter as Float)) {
                    return@runBlocking null
                }
            }
            val metadataJson = Json.parseToJsonElement(metadata.body())
            val hash =
                metadataJson.jsonObject["chapter"]?.jsonObject?.get("hash")!!.toString().trim('"')
            val baseUrl = metadataJson.jsonObject["baseUrl"]!!.toString().trim('"')
            val pages =
                metadataJson.jsonObject["chapter"]?.jsonObject?.get("data")?.jsonArray?.size!!
            database?.let {
                println("setting record to $pages")
                it.recordDao.setRecord(Record(chapterIdentifier, pages))
            }

            val executor = Executors.newFixedThreadPool(
                Math.min(
                    pages,
                    Runtime.getRuntime().availableProcessors() + 4
                )
            )
            val deferreds = mutableListOf<Deferred<Pair<Int, ByteArray>>>()

            suspend fun downloadPage(pageCount: Int, pageDigest: String): Pair<Int, ByteArray> {
                val page = httpClient.get("$baseUrl/data/$hash/${pageDigest.trim('"')}")
                println("Getting $pageDigest")
                return Pair(pageCount + 1, page.readBytes());
            }

            val downloadedPages = mutableListOf<Pair<Int, ByteArray>>()

            for ((pageCount, pageDigest) in metadataJson.jsonObject["chapter"]?.jsonObject?.get("data")?.jsonArray?.withIndex()
                ?: emptyList()) {
                if ((pageCount + 1) !in pagesInDatabase /*&& (generate() ?: true)*/) {
                    val deferred =
                        CoroutineScope(Dispatchers.IO).async {
                            downloadPage(
                                pageCount,
                                pageDigest.toString()
                            )
                        }
                    deferreds.add(deferred)
                } else {
                    println("already in db! $pageDigest")
                }
            }

            for (deferred in deferreds) {
                try {
                    val (pageCount, pageContent) = deferred.await()
                    downloadedPages.add(pageCount to pageContent)
                    /*if (pageDownloadCb != null) {
                        pageDownloadCb(muuid, pageCount to pageContent, pages)
                    }*/
                } catch (e: Exception) {
                    println("An error occurred: $e")
                }
            }

            executor.shutdown()


            // sort them by
            downloadedPages.sortBy { it.first }

            return@runBlocking downloadedPages
        }
    }

    fun resizeImage(byteArray: ByteArray, targetWidth: Int, targetHeight: Int): ByteArray {
        // Convert ByteArray to Bitmap
        val inputStream = ByteArrayInputStream(byteArray)
        val bitmap = BitmapFactory.decodeStream(inputStream)

        // Resize Bitmap
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)

        // Convert resized Bitmap to ByteArray
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    fun getMangaInfo(mangaIdentifier: String): InfoObject {
        return runBlocking {
            val defaultParams = getDefaultRequestParameters().jsonObject.toMutableMap();

            val response = getRequest(
                "$API_URL/manga/$mangaIdentifier?includes%5B%5D=cover_art",
                defaultParams
            )
            val mangaInfoNonagr = Json.parseToJsonElement(response.body())

            val mangaFormat: MutableList<String> = mutableListOf()
            val mangaGenre: MutableList<String> = mutableListOf()
            var coverUrl: String? = null
            val contentRating =
                mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject?.get("contentRating")
                    .toString().trim('"')

            var image: ByteArray? = null
            var smallImage: ByteArray? = null

            for ((index, relationship) in mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("relationships")?.jsonArray?.withIndex()
                ?: emptyList()) {
                if (relationship.jsonObject["type"].toString().trim('"') == "cover_art") {
                    val fileName =
                        mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("relationships")?.jsonArray?.get(
                            index
                        )?.jsonObject?.get(
                            "attributes"
                        )?.jsonObject?.get("fileName").toString().trim('"')
                    coverUrl = "https://mangadex.org/covers/$mangaIdentifier/$fileName"
                }
            }

            for (tag in mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject?.get(
                "tags"
            )?.jsonArray
                ?: emptyList()) {
                if (tag.jsonObject["attributes"]?.jsonObject?.get("group").toString()
                        .trim('"') == "format"
                ) {
                    mangaFormat += tag.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.get(
                        "en"
                    )
                        .toString().trim('"')
                }
                if (tag.jsonObject["attributes"]?.jsonObject?.get("group").toString()
                        .trim('"') == "genre"
                ) {
                    mangaGenre += tag.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.get(
                        "en"
                    )
                        .toString().trim('"')
                }
            }

            if (coverUrl != null) {
                val coverArtResponse = httpClient.get(coverUrl)
                val coverArt = coverArtResponse.readBytes()
                image = coverArt.copyOf()
                smallImage = resizeImage(coverArt.copyOf(), 51, 80)
            }

            val title =
                if (mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject?.get(
                        "title"
                    )?.jsonObject?.get(
                        "en"
                    ) == null
                ) {
                    ((mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject?.get(
                        "title"
                    )?.jsonObject) as Map<*, *>).values.toList()[0]?.toString()
                } else {
                    mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject?.get(
                        "title"
                    )?.jsonObject?.get(
                        "en"
                    ).toString()
                } ?: ""


            return@runBlocking InfoObject(
                identifier = mangaIdentifier,
                name = title,
                description = mangaInfoNonagr.jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject?.get(
                    "description"
                )?.jsonObject?.get(
                    "en"
                )
                    .toString(),
                cover = image,
                smallCover = smallImage,
                mangaFormat = mangaFormat.joinToString("|"),
                mangaGenre = mangaGenre.joinToString("|"),
                contentRating = contentRating
            )
        }
    }

    fun getChapterList(
        mangaIdentifier: String,
        languages: List<String> = listOf("en")
    ): List<JsonElement> {
        return runBlocking {
            val chapters: MutableList<JsonElement> = mutableListOf();
            var offset = 0
            var totalChapters: Int = -1

            while (true) {
                if ((totalChapters != -1) && (totalChapters <= offset)) {
                    break
                }
                val defaultParams = getDefaultRequestParameters().jsonObject.toMutableMap();
                defaultParams["manga"] = JsonPrimitive(mangaIdentifier)
                defaultParams["limit"] = JsonPrimitive(100)
                defaultParams["offset"] = JsonPrimitive(offset)
                defaultParams["translatedLanguage[]"] = Json.encodeToJsonElement(languages)

                val response = getRequest("$API_URL/chapter", defaultParams)

                val responseJson =
                    Json.parseToJsonElement(response.body())

                if (responseJson.jsonObject["data"] == null) {
                    break
                }
                if (totalChapters == -1) {
                    totalChapters = responseJson.jsonObject["total"]?.jsonPrimitive?.int
                        ?: 581 // based on "Golgo 13" chapters
                }
                val chaptersFromApi =
                    responseJson.jsonObject["data"]?.jsonArray?.toList() ?: listOf()
                chapters.addAll(chaptersFromApi)

                offset += 100
            }
            return@runBlocking chapters
        }
    }

    fun getMangaIdentifierFromChapter(chapterIdentifier: String): String? {
        return runBlocking {
            val params = getDefaultRequestParameters()
            val response =
                getRequest("$API_URL/chapter/$chapterIdentifier", params.jsonObject.toMutableMap())
            val responseJson = Json.parseToJsonElement(response.body())
            for (relationship in responseJson.jsonObject["data"]?.jsonObject?.get("relationships")?.jsonArray
                ?: listOf()) {
                if (jsonElementToString(relationship.jsonObject["type"]!!) == "manga") {
                    jsonElementToString(relationship.jsonObject["id"]!!)
                }
            }
            null
        }
    }

    fun getNextAndPrev(
        chapterIdentifier: String,
        mangaIdentifier: String? = null
    ): MutableMap<String, JsonElement?>? {
        return runBlocking {
            var mIdentifier: String? = mangaIdentifier
            if (mIdentifier == null) {
                mIdentifier = getMangaIdentifierFromChapter(chapterIdentifier)
                if (mIdentifier == null) {
                    println("Could not get mangaIdentifier")
                    return@runBlocking null
                }
            }
            if (database == null) {
                println("Database must be passed to backend!")
                return@runBlocking null
            }
            try {
                val nextPrev: MutableMap<String, JsonElement?> = mutableMapOf(
                    Pair("next", JsonObject(mapOf())),
                    Pair("prev", JsonObject(mapOf())),
                    Pair("current", JsonObject(mapOf()))
                )
                var chapterList: List<JsonElement> = listOf();
                try {
                    val connectionChapterList = getChapterList(mIdentifier);
                    database.chapterInfoDao.setChapterInfo(
                        ChapterInfo(
                            mIdentifier,
                            connectionChapterList.toString()
                        )
                    )
                    chapterList = connectionChapterList
                } catch (e: Exception) {
                    val connectionChapterList = database.chapterInfoDao.getChapterInfo(mIdentifier)
                        ?: return@runBlocking null
                    chapterList = Json.parseToJsonElement(connectionChapterList.jsonData).jsonArray
                }

                chapterList.sortedWith(
                    compareBy(
                        { jsonElementToString(it.jsonObject["attributes"]?.jsonObject?.get("volume")!!).toFloatOrNull() },
                        { jsonElementToString(it.jsonObject["attributes"]?.jsonObject?.get("chapter")!!).toFloat() })
                )
                for (chapter in chapterList.withIndex()) {
                    if (jsonElementToString(chapter.value.jsonObject["id"]!!) == chapterIdentifier) {
                        val currentMutableMap = nextPrev["current"]?.jsonObject?.toMutableMap()!!
                        currentMutableMap["chapter"] =
                            chapter.value.jsonObject["attributes"]?.jsonObject?.get("chapter") as JsonElement
                        currentMutableMap["volume"] =
                            chapter.value.jsonObject["attributes"]?.jsonObject?.get("volume") as JsonElement
                        nextPrev["current"] = Json.encodeToJsonElement(currentMutableMap)
                        if (chapter.index > 0) {
                            nextPrev["prev"] = chapterList[chapter.index - 1]
                        }
                        if (chapter.index < chapterList.size - 1) {
                            nextPrev["next"] = chapterList[chapter.index + 1]
                        }
                    }
                }
                val nextPrevJson = Json.encodeToJsonElement(nextPrev)
                if (!database.mangaDao.containsChapter(
                        jsonElementToString(
                            nextPrevJson.jsonObject["next"]?.jsonObject?.get(
                                "id"
                            )
                        )
                    )
                ) {
                    nextPrev["next"] = null
                }
                if (!database.mangaDao.containsChapter(
                        jsonElementToString(
                            nextPrevJson.jsonObject["prev"]?.jsonObject?.get(
                                "id"
                            )
                        )
                    )
                ) {
                    nextPrev["prev"] = null
                }
                return@runBlocking nextPrev
            } catch (e: Exception) {
                e.printStackTrace()
                println("caught exception ${e.message} when trying to get next and prev ")
                return@runBlocking null
            }

        }


    }
}


class UserInfoObject(passedDatabase: MangaDatabase) {
    private val database: MangaDatabase

    companion object {
        @Volatile
        private var instance: UserInfoObject? = null

        fun getInstance(database: MangaDatabase): UserInfoObject {
            if (instance == null) {
                synchronized(UserInfoObject::class.java) {
                    if (instance == null) {
                        instance = UserInfoObject(database)
                    }
                }
            }
            return instance!!
        }
    }

    private var readManga: MutableMap<String, MutableList<String>> = mutableMapOf()
    private var settings: MutableMap<String, String> = mutableMapOf()

    init {
        database = passedDatabase
        var dbValue = runBlocking {
            database.userInfoDao.getUserInfo()
        }
        if (dbValue == null) {
            dbValue = runBlocking {
                database.userInfoDao.setUserInfo(
                    UserInfo(
                        readManga = """{"read_manga": {}}""",
                        settings = "{}"
                    )
                )
                database.userInfoDao.getUserInfo()
            }
        }

        val readMangaJson = dbValue?.let { Json.parseToJsonElement(it.readManga) }
        readMangaJson?.jsonObject?.toMutableMap()?.map {
            readManga[it.key] =
                it.value.jsonArray.toMutableList().map { t -> jsonElementToString(t) }
                    .toMutableList()
        }
        val settingsJson = dbValue?.let { Json.parseToJsonElement(it.settings) }
        settingsJson?.jsonObject?.toMutableMap()?.map {
            settings[it.key] = jsonElementToString(it.value)
        }

    }
}


class DownloadManager(
    passedDatabase: MangaDatabase,
    downloaderAdapter: DownloaderAdapter? = null
) {
    var jobs: MutableList<Pair<String, Job>> = mutableListOf()
    var uiElements: HashMap<String, Pair<ProgressBar, TextView>> = hashMapOf()

    private var connection: MangaDexConnection
    private var database: MangaDatabase
    private var adapter: DownloaderAdapter?

    companion object {
        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(database: MangaDatabase): DownloadManager {
            if (instance == null) {
                synchronized(DownloadManager::class.java) {
                    if (instance == null) {
                        instance = DownloadManager(database)
                    }
                }
            }
            return instance!!
        }
    }

    init {
        connection = MangaDexConnection(passedDatabase)
        database = passedDatabase
        adapter = downloaderAdapter
    }

    fun setAdapter(passedAdapter: DownloaderAdapter?) {
        if (passedAdapter != null) {
            for (item in passedAdapter.downloadsList) {
                for (job in jobs.withIndex()) {
                    if (job.value.first == item && job.value.second.isCompleted) {
                        passedAdapter.onFinish(job.value.first)
                    }
                }
            }
        }
        adapter = passedAdapter
    }


    fun downloadManga(mangaIdentifier: String) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadChapters(mangaIdentifier)
                withContext(Dispatchers.Main) {
                    val uiElement = uiElements[mangaIdentifier] ?: return@withContext
                    uiElement.first.progress = 100
                    uiElement.second.text = "Finished"
                }
                adapter?.onFinish(mangaIdentifier)
            } catch (e: Exception) {
                println(e.message)
            }
        }
        jobs.add(Pair(mangaIdentifier, job))
    }

    fun removeDownload(mangaIdentifier: String) {
        for (job in jobs.withIndex()) {
            if (job.value.first == mangaIdentifier) {
                job.value.second.cancel()
                jobs.removeAt(job.index)
                break
            }
        }
    }

    private suspend fun downloadChapters(mangaIdentifier: String) {
        withContext(Dispatchers.IO) {
            val chapterList = connection.getChapterList(mangaIdentifier)

            for (chapter in chapterList.withIndex()) {

                val chapterIdentifier = chapter.value.jsonObject["id"].toString().trim('"')
                val dbPages = database.mangaDao.getPages(chapterIdentifier)

                val shouldSkip = database.recordDao.getRecord(chapterIdentifier)?.let {
                    it.pages == dbPages.size
                } ?: false

                if (shouldSkip) {
                    println("skipping chapter in database")
                    continue
                }

                val downloadedPages =
                    connection.downloadMangaPages(chapterIdentifier, dbPages, false) {
                        println("Warning hit the rate limit! sleeping for $it seconds")
                        Thread.sleep(((it + 5) * 1000).toLong())
                        true
                    }
                if (downloadedPages.isNullOrEmpty()) {
                    println("Could not download $chapterIdentifier")
                    continue
                }
                val infoIdentifier = database.infoDao.getInfoIdentifier(mangaIdentifier)
                for (page in downloadedPages) {
                    database.mangaDao.setManga(
                        Manga(
                            identifier = chapterIdentifier,
                            page = page.first,
                            data = page.second,
                            infoId = infoIdentifier
                        )
                    )
                }

                val progress = (chapter.index * 100 / chapterList.size)
                withContext(Dispatchers.Main) {
                    val uiElement = uiElements[mangaIdentifier] ?: return@withContext
                    uiElement.first.progress = progress
                    uiElement.second.text = "$progress%"
                }
            }
        }
    }
}


/*class MangaDatabase {
    private val dbConnection = Database.connect("jdbc:sqlite:manga.db", "org.sqlite.JDBC")

    init {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(dbConnection) {
            SchemaUtils.create(Info)
            SchemaUtils.create(Mangas)
            SchemaUtils.create(Records)
            exec("CREATE INDEX IF NOT EXISTS idx_info_id ON mangas(info_id)")
            exec("CREATE INDEX IF NOT EXISTS idx_identifier_page ON mangas (identifier, page);")
            commit()
        }
    }

    fun setPages(chapterIdentifier: String, mangaIdentifier: String, downloadedPage: List<Pair<Int, ByteArray>>) {
        transaction {
            val inInfos = Info.select(Info.identifier).where {Info.identifier eq mangaIdentifier}.singleOrNull()
            var infoIdInDb: String? = null

            if(inInfos != null){
                infoIdInDb = mangaIdentifier
            }

            for (manga: Pair<Int, ByteArray> in downloadedPage) {
                Mangas.insert {
                    it[identifier] = chapterIdentifier
                    it[page] = manga.first
                    it[data] = ExposedBlob(manga.second)
                    it[infoId] = infoIdInDb
                }
            }
            commit()
        }
    }

    fun setRecord(chapterIdentifier: String, pagesCount: Int) {
        transaction {
            Records.insert {
                it[identifier] = chapterIdentifier
                it[pages] = pagesCount
            }
            commit()
        }
    }

    fun getChapterPages(chapterIdentifier: String): List<Int> {
        val pagesInDb = mutableListOf<Int>()
        transaction {
            val pages = Mangas.select(Mangas.page).where { Mangas.identifier eq chapterIdentifier }
                .mapTo(pagesInDb) { it[Mangas.page] }
        }
        return pagesInDb
    }

    fun setInfo(infoObject: InfoObject) {
        transaction {
            Info.insert {
                it[identifier] = infoObject.identifier
                it[name] = infoObject.name
                it[description] = infoObject.description
                it[cover] = infoObject.cover?.let { it1 -> ExposedBlob(it1) }
                it[smallCover] = infoObject.smallCover?.let { it1 -> ExposedBlob(it1) }
                it[mangaFormat] = infoObject.mangaFormat
                it[mangaGenre] = infoObject.mangaGenre
                it[contentRating] = infoObject.contentRating
            }
            commit()
        }
    }
}*/