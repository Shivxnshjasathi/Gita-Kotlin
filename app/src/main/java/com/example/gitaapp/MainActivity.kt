package com.example.gitaapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.*
import kotlin.math.roundToInt
import com.example.gitaapp.ui.theme.AppTypography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme




// --- DATA MODELS ---
data class Chapter(
    val id: Int, val name: String, val name_transliterated: String,
    val name_translation: String, val verses_count: Int, val chapter_number: Int,
    val name_meaning: String, val chapter_summary: String, val chapter_summary_hindi: String,
    val image_name: String? = null
)
data class Verse(
    val id: Int, val verse_number: Int, val chapter_number: Int,
    val text: String, val transliteration: String, val word_meanings: String
)
data class Translation(
    val author_name: String?, val description: String, val verse_id: Int
)
data class Commentary(
    val author_name: String?, val description: String, val verse_id: Int
)

// --- SHARED PREFS KEYS ---
const val PREFS_NAME = "gita_app_prefs"
const val KEY_BOOKMARKS = "bookmarks"
const val KEY_NOTES = "notes"
const val KEY_READ = "read"
const val KEY_FONT_SIZE = "font_size"

// --- ASSET LOADER ---
object AssetLoader {
    private val gson = Gson()
    private fun <T> loadJson(context: Context, file: String, type: TypeToken<T>): T? =
        try {
            gson.fromJson(
                context.assets.open(file).bufferedReader().use { it.readText() },
                type.type
            )
        } catch (e: IOException) { null }
    fun loadChapters(c: Context) = loadJson(c, "chapters.json", object : TypeToken<List<Chapter>>() {}) ?: emptyList()
    fun loadVerses(c: Context) = loadJson(c, "verse.json", object : TypeToken<List<Verse>>() {}) ?: emptyList()
    fun loadTranslations(c: Context) = loadJson(c, "translation.json", object : TypeToken<List<Translation>>() {}) ?: emptyList()
    fun loadCommentaries(c: Context) = loadJson(c, "commentary.json", object : TypeToken<List<Commentary>>() {}) ?: emptyList()
}

// --- NAVIGATION ---
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    data class Chapter(val chapterNumber: Int) : Screen()
    data class Verse(val verseId: Int, val chapterNumber: Int) : Screen()
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BhagavadGitaTheme { GitaApp(applicationContext) } }
    }
}

// --- STATE PROVIDER ---
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var bookmarks by mutableStateOf(loadSet(KEY_BOOKMARKS))
    var notes by mutableStateOf(loadNotes())
    var readVerses by mutableStateOf(loadSet(KEY_READ))
    var fontSize by mutableStateOf(prefs.getInt(KEY_FONT_SIZE, 18))

    fun isBookmarked(id: Int) = bookmarks.contains(id)
    fun toggleBookmark(id: Int) { val set = bookmarks.toMutableSet(); if (!set.remove(id)) set.add(id); bookmarks = set; storeSet(KEY_BOOKMARKS, set) }
    fun getNote(id: Int) = notes[id] ?: ""
    fun setNote(id: Int, note: String) { notes = notes.toMutableMap().apply { if (note.isBlank()) remove(id) else put(id, note) }; storeNotes(notes) }
    fun markRead(id: Int) { if (!readVerses.contains(id)) { val set = readVerses.toMutableSet(); set.add(id); readVerses = set; storeSet(KEY_READ, set) } }
    fun isRead(id: Int) = readVerses.contains(id)
    fun updateFontSize(size: Int) { fontSize = size; prefs.edit().putInt(KEY_FONT_SIZE, size).apply() }

    // PREFS HELPERS
    private fun loadSet(key:String) = prefs.getStringSet(key,null)?.mapNotNull{it?.toIntOrNull()}?.toSet() ?: emptySet()
    private fun storeSet(key:String, set:Set<Int>) = prefs.edit().putStringSet(key, set.map{it.toString()}.toSet()).apply()
    private fun loadNotes(): Map<Int,String> = prefs.getString(KEY_NOTES,"")!!.split("|").filter{it.contains("::")}.mapNotNull{
        val (id,note) = it.split("::", limit=2); id.toIntOrNull()?.let{id1->id1 to note}
    }.toMap()
    private fun storeNotes(map:Map<Int,String>) = prefs.edit().putString(KEY_NOTES, map.entries.joinToString("|"){ "${it.key}::${it.value}" }).apply()
}

// --- APP BAR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    screen: Screen,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = when (screen) {
                    is Screen.Home -> "Gita."
                    is Screen.Chapter -> "Chapters."
                    is Screen.Verse -> "Verse."
                    is Screen.Settings -> "Settings."
                    else -> ""
                }
            )
        },
        navigationIcon = {
            if (screen !is Screen.Home) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {
            if (screen is Screen.Home) {
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

// --- HOME SCREEN ---
@Composable
fun GitaApp(context: Context) {
    val chapters = remember { AssetLoader.loadChapters(context) }
    val verses = remember { AssetLoader.loadVerses(context) }
    val translations = remember { AssetLoader.loadTranslations(context) }
    val commentaries = remember { AssetLoader.loadCommentaries(context) }
    val appState = remember { AppState(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var searchQuery by remember { mutableStateOf("") }
    val fontSize = appState.fontSize.sp
    val dailyVerse = remember { selectDailyVerse(verses) }

    Scaffold(
        topBar = { TopBar(screen, onBack = { screen = Screen.Home }, onSettings = { screen = Screen.Settings }) }
    ) { paddingVals ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingVals)
        ) {
            when (screen) {
                is Screen.Home -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            DailyVerseCard(dailyVerse, fontSize) {
                                screen = Screen.Verse(dailyVerse.id, dailyVerse.chapter_number)
                            }
                            Spacer(Modifier.height(12.dp))
                            SearchBar(searchQuery) { searchQuery = it }
                            Spacer(Modifier.height(5.dp))
                            ProgressBar(appState.readVerses.size, verses.size)
                        }
                        val filteredChapters = if (searchQuery.isBlank())
                            chapters else chapters.filter {
                            it.name_transliterated.contains(searchQuery, true) ||
                                    it.name.contains(searchQuery, true) ||
                                    it.chapter_summary.contains(searchQuery, true)
                        }
                        items(filteredChapters) { ch ->
                            ChapterListItem(
                                ch, appState,
                                onClick = { screen = Screen.Chapter(ch.chapter_number) },
                                fontSize = fontSize
                            )
                        }
                    }
                }
                is Screen.Chapter -> {
                    val chap = chapters.find { it.chapter_number == (screen as Screen.Chapter).chapterNumber }
                    if (chap != null) ChapterDetailScreen(
                        chap,
                        verses.filter { it.chapter_number == chap.chapter_number },
                        fontSize = fontSize,
                        searchQuery = searchQuery,
                        onVerseClick = { vId -> screen = Screen.Verse(vId, chap.chapter_number) },
                        onBack = { screen = Screen.Home },
                        appState = appState,
                    )
                }
                is Screen.Verse -> {
                    val verseList = verses.filter { it.chapter_number == (screen as Screen.Verse).chapterNumber }
                    val idx = verseList.indexOfFirst { it.id == (screen as Screen.Verse).verseId }
                    if (idx >= 0) {
                        VerseDetailScreen(
                            verse = verseList[idx],
                            prev = verseList.getOrNull(idx-1)?.id,
                            next = verseList.getOrNull(idx+1)?.id,
                            translations = translations.filter { it.verse_id == verseList[idx].id },
                            commentaries = commentaries.filter { it.verse_id == verseList[idx].id },
                            fontSize = fontSize,
                            appState = appState,
                            onPrev = { prev -> prev?.let { screen = Screen.Verse(it, (screen as Screen.Verse).chapterNumber) } },
                            onNext = { next -> next?.let { screen = Screen.Verse(it, (screen as Screen.Verse).chapterNumber) } },
                            onBack = { screen = Screen.Chapter((screen as Screen.Verse).chapterNumber) }
                        )
                    }
                }
                is Screen.Settings -> SettingsScreen(
                    fontSize = fontSize.value,
                    onFontSizeChange = { appState.updateFontSize(it) },
                    onBack = { screen = Screen.Home }
                )
            }
        }
    }
}

// --- SEARCH BAR ---

@Composable
fun SearchBar(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search chapters.") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Icon"
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
}



@Composable
fun ProgressBar(read: Int, total: Int) {
    if (total == 0) return
    val pct = ((read.toFloat() / total) * 100).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        // This line centers the children horizontally
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = read / total.toFloat(),
            trackColor = MaterialTheme.colorScheme.secondaryContainer
        )
        Text(
            text = "$read of $total verses read ($pct%)",
            style = MaterialTheme.typography.bodySmall
        )
    }
}


fun selectDailyVerse(verses: List<Verse>): Verse = if (verses.isEmpty()) Verse(0,0,0,"...","...","...")
else verses[Calendar.getInstance().get(Calendar.DAY_OF_YEAR)%verses.size]

@Composable
fun DailyVerseCard(
    verse: Verse,
    fontSize: TextUnit,
    onTap: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        //colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        //elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clickable { onTap() }
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
        ) {
            Text(
                text = "Verse of the Day.",
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = verse.text.take(160).trim(),
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}


// --- CHAPTER LIST CARD ---
@Composable
fun ChapterListItem(ch: Chapter, appState: AppState, onClick: ()->Unit, fontSize: TextUnit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        //elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ch.image_name?.let {
                val context = LocalContext.current
                val resId = context.resources.getIdentifier(it, "drawable", context.packageName)
                if (resId != 0)
                    Image(painterResource(id = resId), null,
                        Modifier.size(60.dp).aspectRatio(1f).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(30.dp))
                    )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${ch.name_transliterated}",
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = fontSize)
                //Text(ch.name, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.secondary, fontSize = fontSize)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(ch.name, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("|", color = MaterialTheme.colorScheme.primary,)
                    Spacer(Modifier.width(8.dp))
                    OutlinedCardChip("${ch.verses_count} verses", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary)

                }
                //OutlinedCardChip("${ch.verses_count} verses", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary)
            }
            if (appState.isBookmarked(ch.id)) {
                Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun OutlinedCardChip(text: String, bg: Color, fg: Color) {
     Text(text, fontSize = 10.sp,)
}

// --- CHAPTER DETAIL SCREEN (Bountiful) ---
@Composable
fun ChapterDetailScreen(
    chapter: Chapter,
    verses: List<Verse>,
    fontSize: TextUnit,
    searchQuery: String,
    onVerseClick: (Int) -> Unit,
    onBack: () -> Unit,
    appState: AppState,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "    अध्याय ${chapter.chapter_number}: ${chapter.name_meaning}",
            fontWeight = FontWeight.Bold, fontSize = fontSize,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            //colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
           // elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Summary", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    chapter.chapter_summary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = fontSize,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader("Verses (${chapter.verses_count})")
        val filtered = if (searchQuery.isBlank()) verses
        else verses.filter { it.text.contains(searchQuery, true) || it.transliteration.contains(searchQuery, true) }
        filtered.forEach { verse ->
            VerseListItem(
                verse = verse,
                isBookmarked = appState.isBookmarked(verse.id),
                isRead = appState.isRead(verse.id),
                hasNote = appState.getNote(verse.id).isNotBlank(),
                fontSize = fontSize,
                onClick = {
                    appState.markRead(verse.id)
                    onVerseClick(verse.id)
                },
                onBookmark = { appState.toggleBookmark(verse.id) }
            )
        }
    }
}

// --- VERSE CARD (Bountiful) ---
@Composable
fun VerseListItem(
    verse: Verse,
    isBookmarked: Boolean,
    isRead: Boolean,
    hasNote: Boolean,
    fontSize: TextUnit,
    onClick: ()->Unit,
    onBookmark: ()->Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),

        // elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (isRead) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(15.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Verse ${verse.chapter_number}.${verse.verse_number}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    verse.text.take(120).trim(),
                    fontSize = fontSize, maxLines = 3,
                    color = if (hasNote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Default.Favorite else Icons.Outlined.Favorite,
                    null,
                    tint = if (isBookmarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
            if (hasNote) Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

// --- VERSE DETAIL SCREEN (Bountiful) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseDetailScreen(
    verse: Verse,
    prev: Int?,
    next: Int?,
    translations: List<Translation>,
    commentaries: List<Commentary>,
    fontSize: TextUnit,
    appState: AppState,
    onPrev: (Int?)->Unit,
    onNext: (Int?)->Unit,
    onBack:()->Unit
) {
    val context = LocalContext.current
    var noteDialogOpen by remember { mutableStateOf(false) }
    var currNote by remember { mutableStateOf(appState.getNote(verse.id)) }
    Surface(Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .pointerInput(Unit) {
            detectHorizontalDragGestures { _, dragAmount ->
                if (dragAmount > 24 && prev!=null) onPrev(prev)
                else if (dragAmount < -24 && next!=null) onNext(next)
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    //colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                  //  elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Verse ${verse.chapter_number}.${verse.verse_number}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            verse.text.trim(),
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { appState.toggleBookmark(verse.id) }) {
                        val favorite = appState.isBookmarked(verse.id)
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Outlined.Favorite,
                            null, tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = { noteDialogOpen = true }) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND)
                        sendIntent.type = "text/plain"
                        sendIntent.putExtra(Intent.EXTRA_TEXT, verse.text)
                        ContextCompat.startActivity(context, Intent.createChooser(sendIntent, "Share verse"), null)
                    }) {
                        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Verse", verse.text))
                        Toast.makeText(context,"Copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.AddCircle, null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (prev != null)
                        TextButton(onClick={onPrev(prev)}) { Icon(Icons.Default.ArrowBack,null); Text("Previous") }
                    if (next != null)
                        TextButton(onClick={onNext(next)}) { Text("Next"); Icon(Icons.Default.ArrowForward,null) }
                }
            }
            item { InfoCard("Transliteration", verse.transliteration, fontSize) }
            item { InfoCard("Word Meanings", verse.word_meanings, fontSize) }
            if (translations.isNotEmpty()) {
                item { SectionHeader("Translations (${translations.size})") }
                items(translations) { t -> AuthorCommentaryCard(t.author_name, t.description, fontSize) }
            }
            if (commentaries.isNotEmpty()) {
                item { SectionHeader("Commentaries (${commentaries.size})") }
                items(commentaries) { c -> AuthorCommentaryCard(c.author_name, c.description, fontSize) }
            }
        }
        if (noteDialogOpen) {
            NoteDialog(
                initial = currNote,
                onSave = { n -> appState.setNote(verse.id, n); currNote = n; noteDialogOpen = false },
                onDismiss = { noteDialogOpen = false }
            )
        }
    }
}

// --- SECTION HEADER CHIP ---
@Composable
fun SectionHeader(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
        )
    }
}

// --- INFO / COMMENTARY CARD ---
@Composable
fun InfoCard(title: String, content: String, fontSize: TextUnit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        //colors = CardDefaults.cardColors(containerColor = Color.Transparent),
       // elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(content, fontSize = fontSize, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun AuthorCommentaryCard(author: String?, text: String, fontSize: TextUnit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        //elevation = CardDefaults.cardElevation(0.5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(author?.takeIf{it.isNotBlank()} ?: "Author", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(text.trim(), fontSize = fontSize, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

// --- NOTES DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(initial: String, onSave: (String)->Unit, onDismiss: ()->Unit) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Note/Highlight") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Your note") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- FONT & ACCESSIBILITY SETTINGS SCREEN ---
@Composable
fun SettingsScreen(fontSize: Float, onFontSizeChange: (Int)->Unit, onBack: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 23.sp)
        Spacer(Modifier.height(14.dp))
        Text("Font Size", fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        Slider(
            value = fontSize,
            valueRange = 12f..30f,
            steps = 9,
            onValueChange = { onFontSizeChange(it.roundToInt()) }
        )
        Text("${fontSize.roundToInt()} sp")
        Spacer(Modifier.height(40.dp))
        Button(onClick = onBack, shape = RoundedCornerShape(16.dp)) { Text("Back") }
    }
}

// --- BEAUTIFUL, SPIRITUAL LIGHT THEME ---
@Composable
fun BhagavadGitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF23272A),           // Elegant deep gray-blue for headline accents
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE9EEF5),  // Airy, fresh blue-tinted off-white for cards/surfaces
            onPrimaryContainer = Color(0xFF272C31),

            secondary = Color(0xFFB5D0E6),         // Misty blue as a gentle highlight/accent
            onSecondary = Color(0xFF22334A),
            secondaryContainer = Color(0xFFF1F7FA),// Softest hint of cloud blue for chips/highlights
            onSecondaryContainer = Color(0xFF53616C),

            background = Color(0xFFF6F8FA),        // Balanced, exceptionally bright off-white background
            onBackground = Color(0xFF23272A),      // Deep blue-gray for readability

            surface = Color(0xFFFFFFFF),           // Radiant pure white for sheets/dialogs/cards
            onSurface = Color(0xFF373D48),         // Gentle neutral gray for card text

            surfaceVariant = Color(0xFFE6E8F0),    // Subtle cool gray for soft separation
            onSurfaceVariant = Color(0xFF76808E),  // Muted blue-gray for details

            outline = Color(0xFFD1D7DE),           // Ultra-light washed blue-gray for borders
            error = Color(0xFFB00020)              // Standard, softened Material red
        )




        ,
                typography = AppTypography,  // Use correct variable
        content = content
    )
}
