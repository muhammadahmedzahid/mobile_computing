package com.example.myapplication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.room.Dao
import androidx.room.Database
import coil.compose.rememberAsyncImagePainter
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            AppContent()

        }
    }
}


@Entity(tableName = "settings_table")
data class SettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val imageUri: String?
)

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSettings(settings: SettingsEntity)

    @Query("SELECT * FROM settings_table ORDER BY id DESC LIMIT 1")
    fun getSettings(): SettingsEntity?
}

@Database(entities = [SettingsEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
}

fun copyImageToFolder(originalUri: Uri, folderName: String, context: Context): Uri? {
    val sourceInputStream = context.contentResolver.openInputStream(originalUri)
    val copiedUri = try {
        val fileExtension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(originalUri))
        val fileName = "copied_image_${System.currentTimeMillis()}.$fileExtension"
        val folder = File(context.filesDir, folderName)

        if (!folder.exists()) {
            folder.mkdirs()
        }

        val copiedFile = File(folder, fileName)
        val outputStream = FileOutputStream(copiedFile)

        sourceInputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        // Return the URI of the copied image
        Uri.fromFile(copiedFile)
    } catch (e: Exception) {
        Log.d("Hello","$e.printStackTrace()")

        e.printStackTrace()

        null
    } finally {

        sourceInputStream?.close()
    }

    return copiedUri
}

class SettingsRepository(private val settingsDao: SettingsDao) {
    suspend fun saveSettings(text: String, imageUri: String) {
        try {
            val settings = SettingsEntity(text = text, imageUri = imageUri)
            withContext(Dispatchers.IO) {
                settingsDao.insertSettings(settings)
            }
            Log.d("SettingsRepository", "Settings saved successfully")
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Error saving settings: ${e.message}", e)
        }
    }

    suspend fun getSettings(): SettingsEntity? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSettings()
        }
    }
}

data class Message(val author: String, val body: String)

@Composable
fun MessageCard(msg: Message, settingsRepository: SettingsRepository?) {
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(key1 = true) {
        val existingSettings = settingsRepository?.getSettings()
        if (existingSettings != null) {
            inputText = existingSettings.text ?: ""
            existingSettings.imageUri?.let { uriString ->
                selectedImageUri = existingSettings.imageUri.let { Uri.parse(it) }
            }
        }
    }
    Row(modifier = Modifier.padding(all = 8.dp)) {
        // Use the image from the database if available, otherwise use the default image
        val imagePainter = rememberAsyncImagePainter(model = selectedImageUri)
        when (val state = imagePainter.state) {
            is AsyncImagePainter.State.Success -> {
                Log.d("ImageLoadingMain", "Image loaded successfully")
            }

            is AsyncImagePainter.State.Error -> {
                Log.e("ImageLoadingMain", "Error loading image. Details: ${state.toString()}")
            }

            else -> {
                // Image is still loading or in another state
            }
        }

        Image(
            painter = imagePainter,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // We keep track if the message is expanded or not in this
        // variable
        var isExpanded by remember { mutableStateOf(false) }

        // We toggle the isExpanded variable when we click on this Column
        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = inputText,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = msg.body,
                    modifier = Modifier.padding(all = 4.dp),
                    // If the message is expanded, we display all its content
                    // otherwise we only display the first line
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * SampleData for Jetpack Compose Tutorial
 */
object SampleData {
    // Sample conversation data
    val conversationSample = listOf(
        Message(
            "Lexi",
            "Test...Test...Test..."
        ),
        Message(
            "Lexi",
            """List of Android versions:
            |Android KitKat (API 19)
            |Android Lollipop (API 21)
            |Android Marshmallow (API 23)
            |Android Nougat (API 24)
            |Android Oreo (API 26)
            |Android Pie (API 28)
            |Android 10 (API 29)
            |Android 11 (API 30)
            |Android 12 (API 31)""".trim()
        ),
        Message(
            "Lexi",
            """I think Kotlin is my favorite programming language.
            |It's so much fun!""".trim()
        ),
        Message(
            "Lexi",
            "Searching for alternatives to XML layouts..."
        ),
        Message(
            "Lexi",
            """Hey, take a look at Jetpack Compose, it's great!
            |It's the Android's modern toolkit for building native UI.
            |It simplifies and accelerates UI development on Android.
            |Less code, powerful tools, and intuitive Kotlin APIs :)""".trim()
        ),
        Message(
            "Lexi",
            "It's available from API 21+ :)"
        ),
        Message(
            "Lexi",
            "Writing Kotlin for UI seems so natural, Compose where have you been all my life?"
        ),
        Message(
            "Lexi",
            "Android Studio next version's name is Arctic Fox"
        ),
        Message(
            "Lexi",
            "Android Studio Arctic Fox tooling for Compose is top notch ^_^"
        ),
        Message(
            "Lexi",
            "I didn't know you can now run the emulator directly from Android Studio"
        ),
        Message(
            "Lexi",
            "Compose Previews are great to check quickly how a composable layout looks like"
        ),
        Message(
            "Lexi",
            "Previews are also interactive after enabling the experimental setting"
        ),
        Message(
            "Lexi",
            "Have you tried writing build.gradle with KTS?"
        ),
    )
}


enum class Screen {
    Conversation,
    Settings
}

@Composable
fun AppContent() {
    var currentView by remember { mutableStateOf<Screen>(Screen.Conversation) }

    val context = LocalContext.current

    // Handle Android back button press
    DisposableEffect(currentView) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentView != Screen.Conversation) {
                    currentView = Screen.Conversation
                } else {
                    context?.let { ctx ->
                        (ctx as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher?.onBackPressed()
                    }
                }
            }
        }
        (context as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher?.addCallback(
            callback
        )
        onDispose {
            callback.remove()
        }
    }

    // Display the appropriate view based on the current screen
    val appDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java, "my_local_database"
    ).build()

    val settingsDao = appDatabase.settingsDao()
    val settingsRepository = SettingsRepository(settingsDao)
    when (currentView) {
        Screen.Conversation -> {
            ConversationView(
                navigateTo = { newView -> currentView = newView },
                settingsRepository = settingsRepository
            )
        }

        Screen.Settings -> {
            SettingsView(
                navigateTo = { newView -> currentView = newView },
                settingsRepository = settingsRepository
            )
        }
    }
}


@Composable
fun ConversationView(navigateTo: (Screen) -> Unit, settingsRepository: SettingsRepository) {
    // Load existing settings when the screen is created
    var existingSettingsState by remember { mutableStateOf<SettingsEntity?>(null) }

    LazyColumn {
        items(SampleData.conversationSample) { message ->
            MessageCard(message, settingsRepository)
        }
        item {
            // Add a button to navigate to SettingsView
            NavigationButton(
                onClick = { navigateTo(Screen.Settings) },
                label = "Settings",
                icon = Icons.Default.Settings
            )
        }
    }
    // Launch a coroutine when the composable is first launched


}


@Composable
fun SettingsView(navigateTo: (Screen) -> Unit, settingsRepository: SettingsRepository) {


    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ActivityResultLauncher for selecting a single image
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            // Callback is invoked after the user selects a media item or closes the photo picker.
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                selectedImageUri = uri
                val copiedUri = copyImageToFolder(uri, "mobile_computing", context)
                selectedImageUri = copiedUri
                Log.d("PhotoPicker", "$copiedUri")
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
    // Load existing settings when the screen is created
    LaunchedEffect(key1 = true) {
        val existingSettings = settingsRepository.getSettings()
        if (existingSettings != null) {
            inputText = existingSettings.text
            selectedImageUri = existingSettings.imageUri?.let { Uri.parse(it) }

            Log.d("selectedImageUri", selectedImageUri.toString())
        }
    }
    // Save settings when the user clicks a button
    Button(
        onClick = {
            selectedImageUri?.let { uri ->
                val imageUriString = uri.toString()
                Log.d("SavingURI:", selectedImageUri.toString())
                coroutineScope.launch {
                    settingsRepository.saveSettings(inputText, imageUriString)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Save Settings")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Input text field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter some text") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Box(
            modifier = Modifier
                .size(100.dp)
                .padding(16.dp)
                .clickable {
                    // Launch the photo picker and let the user choose images and videos.
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
                .border(
                    width = 2.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(4.dp)
                ) // Add border
        ) {
            // Display the selected image if available

            selectedImageUri?.let { uri ->
                Log.d("ImageLoading", "Attempting to load image from URI: $uri")
                val imagePainter = rememberAsyncImagePainter(uri)
                Log.d("ImagePainter", imagePainter.toString())

                when (val state = imagePainter.state) {
                    is AsyncImagePainter.State.Success -> {
                        Log.d("ImageLoading", "Image loaded successfully")
                    }

                    is AsyncImagePainter.State.Error -> {
                        Log.e(
                            "ImageLoading",
                            "Error loading image. Details: ${state.toString()}"
                        )
                    }

                    else -> {
                        // Image is still loading or in another state
                    }
                }

                Image(
                    painter = imagePainter,
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxSize()
                )
            }


            // Show border when no image is selected
            if (selectedImageUri == null) {
                Log.d("No Image:", "Setting image called.$selectedImageUri")

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.2f))
                )
            }
        }


        // Add a button to go back to ConversationView
        NavigationButton(
            onClick = { navigateTo(Screen.Conversation) },
            label = "Back to Conversation",
            icon = Icons.Default.ArrowBack
        )
    }
}


@Composable
fun NavigationButton(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.White)
    }
}


