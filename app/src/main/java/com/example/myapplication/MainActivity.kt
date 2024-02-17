package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
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
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.os.IBinder
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import coil.compose.rememberImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity(), OrientationSensorHandler.OrientationChangeListener {

    private lateinit var sensorHandler: OrientationSensorHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppContent()
        }
        // Initialize and register the sensor handler
        sensorHandler = OrientationSensorHandler(this, this)

    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the sensor listener when the activity is destroyed
        sensorHandler.unregisterSensorListener()
    }

    override fun onOrientationChanged(x: Float, y: Float, z: Float) {
        // Handle orientation changes here
        // Now I want to publish those values into the Notification.
        val value = "X: $x, Y: $y, Z: $z"
        Log.d("OrientationChange", value)
        showAppStartedNotification(this, value)

    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        if (intent?.action == "OPEN_APP_ACTION") {
            Log.d("OpeningApp", "Stage_01")
            val startIntent = Intent(this, MainActivity::class.java)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(startIntent)
        }
    }
}


class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "SHOW_NOTIFICATION_ACTION") {
            val value = intent.getStringExtra("orientationValue") ?: ""
            context?.let {
                showAppStartedNotification(it, value)
            }
        }
    }
}

class OrientationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ORIENTATION_CHANGED_ACTION") {
            val x = intent.getFloatExtra("x", 0.0f)
            val y = intent.getFloatExtra("y", 0.0f)
            val z = intent.getFloatExtra("z", 0.0f)
            if (context != null) {
                showAppStartedNotification(context, "X: $x, Y: $y, Z: $z")
            }
        }
    }
}


class OrientationSensorHandler(
    private val context: Context,
    private val listener: OrientationChangeListener
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Threshold to determine if the change in orientation is significant
    private val ORIENTATION_CHANGE_THRESHOLD = 1.0f

    private var lastX = 0.0f
    private var lastY = 0.0f
    private var lastZ = 0.0f

    init {
        registerSensorListener()
    }

    fun registerSensorListener() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun unregisterSensorListener() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this example
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Check if the change in orientation is significant
            if (isOrientationChanged(x, y, z)) {
                listener.onOrientationChanged(x, y, z)
                updateLastOrientation(x, y, z)
            }
        }
    }


    private fun isOrientationChanged(x: Float, y: Float, z: Float): Boolean {
        val deltaX = Math.abs(x - lastX)
        val deltaY = Math.abs(y - lastY)
        val deltaZ = Math.abs(z - lastZ)

        return (deltaX > ORIENTATION_CHANGE_THRESHOLD ||
                deltaY > ORIENTATION_CHANGE_THRESHOLD ||
                deltaZ > ORIENTATION_CHANGE_THRESHOLD)
    }

    private fun updateLastOrientation(x: Float, y: Float, z: Float) {
        lastX = x
        lastY = y
        lastZ = z
    }

    interface OrientationChangeListener {
        fun onOrientationChanged(x: Float, y: Float, z: Float)
    }


}


private fun showAppStartedNotification(context: Context, value: String) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is not in the Support Library.
    Log.d("showAppStartedNotification", "Stage_01")

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("showAppStartedNotification", "Stage_02")

            val name = "SENSOR_CHANNEL"
            val descriptionText = "This is description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("SENSOR_NOTIFICATION_ID", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system.
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            // Create and show a notification with a PendingIntent to open the app
            val intent = Intent(context, MainActivity::class.java)
            intent.action = "OPEN_APP_ACTION" // Add a custom action

            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            val notificationBuilder = NotificationCompat.Builder(context, "SENSOR_NOTIFICATION_ID")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("App Started")
                .setContentText(value)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
            notificationManager.notify(1, notificationBuilder.build())
        }
    } catch (e: Exception) {
        // Handle the exception here
        Log.e("showAppStartedNotification", "Error creating notification channel: ${e.message}")
    }
}


// Function to request notification permission
fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "SENSOR_NOTIFICATION_ID"
        val channelName = "SENSOR_CHANNEL"
        val description = "notify you when the phone rotates."
        val importance = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(channelId, channelName, importance).apply {
            this.description = description
            enableVibration(true)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Launch system notification settings to allow the user to grant notification permission
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
        context.startActivity(intent)
    }
}


@Entity(tableName = "settings_table")
data class SettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val imageUri: String?
)

// Below code will work with the final task where I am inserting the data into the database.
@Entity(tableName = "text_table")
data class TextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String
)

@Entity(tableName = "images_table")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val imagePath: String
)

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSettings(settings: SettingsEntity)

    @Query("SELECT * FROM settings_table ORDER BY id DESC LIMIT 1")
    fun getSettings(): SettingsEntity?
}


@Dao
interface TextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertText(textEntity: TextEntity)

    @Query("SELECT * FROM text_table")
    fun getAllText(): List<TextEntity>
}

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertImage(image: ImageEntity)

    @Query("SELECT * FROM images_table")
    fun getAllImages(): List<ImageEntity>
}

@Database(
    entities = [SettingsEntity::class, TextEntity::class, ImageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun textDao(): TextDao
    abstract fun imageDao(): ImageDao

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

// Getting and Saving the data into the database.
class TextRepository(private val textDao: TextDao) {
    suspend fun saveText(text: String) {
        try {
            val textEntity = TextEntity(text = text)
            withContext(Dispatchers.IO) {
                textDao.insertText(textEntity)
            }
            Log.d("TextRepository", "Text saved successfully")
        } catch (e: Exception) {
            Log.e("TextRepository", "Error saving text: ${e.message}", e)
        }
    }

    suspend fun getAllText(): List<TextEntity> {
        return withContext(Dispatchers.IO) {
            textDao.getAllText()
        }
    }
}

class ImageRepository(private val imageDao: ImageDao) {
    suspend fun saveImage(imagePath: String) {
        try {
            val imageEntity = ImageEntity(imagePath = imagePath)
            withContext(Dispatchers.IO) {
                imageDao.insertImage(imageEntity)
            }
            Log.d("ImageRepository", "Image saved successfully")
        } catch (e: Exception) {
            Log.e("ImageRepository", "Error saving image: ${e.message}", e)
        }
    }

    suspend fun getAllImages(): List<ImageEntity> {
        return withContext(Dispatchers.IO) {
            imageDao.getAllImages()
        }
    }
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
        Log.d("Hello", "$e.printStackTrace()")

        e.printStackTrace()

        null
    } finally {

        sourceInputStream?.close()
    }

    return copiedUri
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
    val texttableDao = appDatabase.textDao()
    val imageDao = appDatabase.imageDao()
    val settingsRepository = SettingsRepository(settingsDao)
    val textRepository = TextRepository(texttableDao)
    val imageRepository = ImageRepository(imageDao)
    when (currentView) {
        Screen.Conversation -> {
            ConversationView(
                navigateTo = { newView -> currentView = newView },
                settingsRepository = settingsRepository,
                textRepository = textRepository

            )
        }

        Screen.Settings -> {
            SettingsView(
                navigateTo = { newView -> currentView = newView },
                settingsRepository = settingsRepository,
                textRepository = textRepository,
                imageRepository = imageRepository
            )
        }
    }
}


@Composable
fun ConversationView(
    navigateTo: (Screen) -> Unit,
    settingsRepository: SettingsRepository,
    textRepository: TextRepository
) {
    // Load existing settings when the screen is created
    var existingSettingsState by remember { mutableStateOf<SettingsEntity?>(null) }
    var textrep by remember { mutableStateOf<List<TextEntity>?>(null) }

    // Fetch the inserted text from the database table.
    LaunchedEffect(key1 = true) {
        textrep = textRepository.getAllText()
        Log.d("ConversationTextRepository22:", textrep.toString())
    }
    Log.d("ConversationTextRepository:", textrep.toString())

    LazyColumn {
//        items(SampleData.conversationSample) { message ->
//            MessageCard(message, settingsRepository)
//        }
        items(textrep ?: emptyList()) { message ->
            MessageCard(Message("", message.text), settingsRepository)
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

// Function to create a temporary file URI
private fun createImageUri(context: Context): Uri {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val imageName = "IMG_$timestamp.jpg"
    val imagesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val imageFile = File(imagesDir, imageName)
    return FileProvider.getUriForFile(context, context.packageName + ".provider", imageFile)
}




@Composable
fun ImageGrid(images: List<Uri>, onItemClick: (Uri) -> Unit) {
    val filePaths = listOf(
        "content://com.example.myapplication.provider/external_files/Android/data/com.example.myapplication/files/Pictures/IMG_20240217_192403.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",
        "file:///data/user/0/com.example.myapplication/files/mobile_computing/copied_image_1708170976169.jpg",

        )
    // Define your Uri class to parse the file paths

    // Function to convert file paths to Uri objects

    val dummy_images = filePaths.map { Uri.parse(it) }

    Log.d("Images:", dummy_images.toString())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(images.chunked(3)) { rowImages ->
            Row {
                for (image in rowImages) {
                    ImageItem(uri = image, onItemClick = onItemClick)


                }
            }
        }
    }
}

@Composable
fun ImageItem(uri: Uri, onItemClick: (Uri) -> Unit) {
    Box(
        modifier = Modifier
            .size(125.dp)
            .padding(4.dp)
            .clickable { onItemClick(uri) }
    ) {
        // Display the image
        val imagePainter = rememberAsyncImagePainter(uri)
        Image(
            painter = imagePainter,
            contentDescription = "Selected Image",
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
fun SettingsView(
    navigateTo: (Screen) -> Unit,
    settingsRepository: SettingsRepository,
    textRepository: TextRepository,
    imageRepository: ImageRepository
) {


    var inputText by remember { mutableStateOf("") }
    var inputText2 by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imagesrep by remember { mutableStateOf<List<ImageEntity>?>(null) }


    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var photoPath: String? = null
    var currentPhotoPath: String? = null // Declare a variable to store the current photo path

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
    // Launch the camera to take a photo
    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                // Handle photo taken successfully
                Log.d("PhotoTaken", "Photo was taken successfully")
                if (photoPath != null) {
                    Log.d("PhotoPath:", photoPath.toString())
//                    Insert the path into database.
                    coroutineScope.launch {
                        imageRepository.saveImage(photoPath.toString())
                    }
                }
                // You may want to do something with the taken photo, like adding it to the list or saving it
            } else {
                // Handle failure to take photo
                Log.d("PhotoTaken", "Failed to take photo")
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




    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )


    {
        // Add a button to go back to ConversationView
        NavigationButton(
            onClick = { navigateTo(Screen.Conversation) },
            label = "Back to Conversation",
            icon = Icons.Default.ArrowBack
        )

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
        val notificationEnabled =
            remember { mutableStateOf(true) } // New state for notification enable/disable

        // Enable Notification button
        Button(
            onClick = {
                notificationEnabled.value = !notificationEnabled.value
                if (notificationEnabled.value) {
                    requestNotificationPermission(context)
                } else {
                    // Handle notification disable logic (e.g., unregister for notifications)
                    // Add your notification disable logic here
                    Log.d("DisableButton Clicked:", "button")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(if (notificationEnabled.value) "Disable Notification" else "Enable Notification")
        }

        // Input text field
        OutlinedTextField(
            value = inputText2,
            onValueChange = { inputText2 = it },
            label = { Text("Add text into database") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        Button(
            onClick = {
                Log.d("InputText2:", inputText2)
                coroutineScope.launch {
                    textRepository.saveText(inputText2)
                }


            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Save into database")
        }
        // Button to open camera and take photo
        Button(
            onClick = {
                // Create a file URI where you want to save the captured image
                val photoUri = createImageUri(context)
                Log.d("Photo URI:", photoUri.toString())
                photoPath = photoUri.toString()
                takePhoto.launch(photoUri)

            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Take Photo")
        }
        // Fetch the inserted text from the database table.
        LaunchedEffect(key1 = true) {
            imagesrep = imageRepository.getAllImages()
            Log.d("GetAllImages:", imagesrep.toString())

        }
        val imagesList: List<Uri> = imagesrep?.map { imageEntity ->
            Uri.parse(imageEntity.imagePath)
        } ?: emptyList()
        // Display grid of images
        ImageGrid(images = imagesList) { clickedUri ->
            // Handle click event
            selectedImageUri = clickedUri
        }


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


