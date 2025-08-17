package com.example.symvora

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import com.example.symvora.ui.theme.SymvoraTheme
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.symvora.DeepseekApi

data class AppColors(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

val LocalAppColors: ProvidableCompositionLocal<AppColors> = staticCompositionLocalOf {
    AppColors(
        primary = Color(0xFF6C63FF),
        background = Color.White,
        surface = Color(0xFFF9F9F9),
        textPrimary = Color(0xFF2E2E2E),
        textSecondary = Color(0xFF666666),
        textTertiary = Color(0xFF999999)
    )
}

// Unified Theme Manager
object ThemeManager {
    var isDarkMode by mutableStateOf(false)
    
    fun toggleTheme() {
        isDarkMode = !isDarkMode
    }
    
    val colors: AppColors
        @Composable
        get() = if (isDarkMode) {
            AppColors(
                primary = Color(0xFF8B85FF),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                textPrimary = Color.White,
                textSecondary = Color(0xFFB3B3B3),
                textTertiary = Color(0xFF808080)
            )
        } else {
            AppColors(
                primary = Color(0xFF6C63FF),
                background = Color.White,
                surface = Color(0xFFF9F9F9),
                textPrimary = Color(0xFF2E2E2E),
                textSecondary = Color(0xFF666666),
                textTertiary = Color(0xFF999999)
            )
        }
}

// Simple in-memory user management
data class User(
    val email: String,
    var name: String,
    var password: String? = null
)

object UserManager {
    var currentUser by mutableStateOf<User?>(null)

    val isAuthenticated: Boolean
        get() = currentUser != null

    fun updateName(newName: String) {
        currentUser?.let { user ->
            currentUser = user.copy(name = newName)
        }
    }
}

// Font Size Manager
object FontSizeManager {
    var fontSize by mutableStateOf("Medium")
    
    val scale: Float
        get() = when (fontSize) {
            "Small" -> 0.85f
            "Large" -> 1.15f
            else -> 1.0f // Medium
        }
    
    fun getScaledSize(baseSize: Float): Float {
        return baseSize * scale
    }
}

// Composable function to get scaled font size
@Composable
fun scaledFontSize(baseSize: Float): Float {
    return FontSizeManager.getScaledSize(baseSize)
}

// Data model for symptom history
data class SymptomHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val symptoms: String,
    val aiResponse: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Firebase integration
object FirebaseService {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    suspend fun loadCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        val uid = firebaseUser.uid
        val doc = db.collection("users").document(uid).get().await()
        val name = doc.getString("name") ?: firebaseUser.email?.substringBefore("@") ?: ""
        val email = firebaseUser.email ?: return null
        return User(email = email, name = name)
    }

    suspend fun signUp(name: String, email: String, password: String): User {
        auth.createUserWithEmailAndPassword(email, password).await()
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("No UID after signup")
        val profile = mapOf(
            "name" to name,
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(profile).await()
        return User(email = email, name = name)
    }

    suspend fun login(email: String, password: String): User {
        auth.signInWithEmailAndPassword(email, password).await()
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("No UID after login")
        val doc = db.collection("users").document(uid).get().await()
        val name = doc.getString("name") ?: email.substringBefore("@")
        return User(email = email, name = name)
    }

    suspend fun updateName(newName: String) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        db.collection("users").document(uid).update("name", newName).await()
    }

    fun logout() {
        auth.signOut()
    }
}

// Global history storage
object SymptomHistoryManager {
    private val _history = mutableStateListOf<SymptomHistoryEntry>()
    private var hasInitialized = false
    
    fun hasBeenInitialized(): Boolean = hasInitialized
    
    fun initializeWithSampleData() {
        if (!hasInitialized) {
            addSampleData()
            hasInitialized = true
        }
    }
    
    fun addEntry(entry: SymptomHistoryEntry) {
        _history.add(0, entry) // Add to beginning
    }
    
    fun getAllEntries(): List<SymptomHistoryEntry> = _history.toList()
    
    fun searchEntries(query: String): List<SymptomHistoryEntry> {
        if (query.isBlank()) return getAllEntries()
        return _history.filter { entry ->
            entry.symptoms.contains(query, ignoreCase = true) ||
            entry.aiResponse.contains(query, ignoreCase = true) ||
            entry.id.contains(query, ignoreCase = true)
        }.sortedByDescending { it.timestamp }
    }
    
    fun clearHistory() {
        _history.clear()
        // Keep initialization flag true so sample data won't be re-added
        // This ensures a true clear operation
    }
    
    fun resetToInitialState() {
        _history.clear()
        hasInitialized = false
        // This would allow sample data to be re-added if needed
    }
    
    // Expose the mutable state list for observation
    fun getHistoryState() = _history
    
    private fun addSampleData() {
        val sampleEntries = listOf(
            SymptomHistoryEntry(
                symptoms = "I have a headache and fever for the past 2 days",
                aiResponse = "Based on your symptoms, here are some general possibilities:\n\nPossible Conditions:\n• Common cold or flu\n• Migraine\n• Tension headache\n\nGeneral Advice:\n• Rest and stay hydrated\n• Take over-the-counter pain relievers\n• Monitor your temperature\n\n⚠️ IMPORTANT: This is for informational purposes only. Always consult a healthcare professional for proper diagnosis and treatment.",
                timestamp = System.currentTimeMillis() - 86400000 // 1 day ago
            ),
            SymptomHistoryEntry(
                symptoms = "Cough and sore throat, feeling tired",
                aiResponse = "Based on your symptoms, here are some general possibilities:\n\nPossible Conditions:\n• Upper respiratory infection\n• Common cold\n• Seasonal allergies\n\nGeneral Advice:\n• Rest and stay hydrated\n• Gargle with warm salt water\n• Use throat lozenges\n\n⚠️ IMPORTANT: This is for informational purposes only. Always consult a healthcare professional for proper diagnosis and treatment.",
                timestamp = System.currentTimeMillis() - 172800000 // 2 days ago
            ),
            SymptomHistoryEntry(
                symptoms = "Stomach pain and nausea after eating",
                aiResponse = "Based on your symptoms, here are some general possibilities:\n\nPossible Conditions:\n• Food poisoning\n• Gastritis\n• Indigestion\n\nGeneral Advice:\n• Stay hydrated with clear fluids\n• Eat bland foods (BRAT diet)\n• Avoid spicy or fatty foods\n\n⚠️ IMPORTANT: This is for informational purposes only. Always consult a healthcare professional for proper diagnosis and treatment.",
                timestamp = System.currentTimeMillis() - 259200000 // 3 days ago
            )
        )
        
        _history.addAll(sampleEntries)
    }
}

enum class Screen {
    Welcome, SignUp, Login, Symptoms, History, Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeepseekApi.setApiKey("sk-4295ec6627ab4663b710bd9a88528d8e")
        enableEdgeToEdge()
        setContent {
            val colors = ThemeManager.colors

            CompositionLocalProvider(LocalAppColors provides colors) {
                SymvoraTheme(darkTheme = ThemeManager.isDarkMode) {
                    var currentScreen by remember { mutableStateOf(Screen.Welcome) }
                    val isAuthenticated by remember { derivedStateOf { UserManager.isAuthenticated } }

                    // Attempt to auto-login existing Firebase user
                    LaunchedEffect(Unit) {
                        try {
                            val loaded = FirebaseService.loadCurrentUser()
                            if (loaded != null) {
                                UserManager.currentUser = loaded
                            }
                        } catch (_: Exception) { }
                    }

                    // Enforce authentication gating
                    LaunchedEffect(isAuthenticated, currentScreen) {
                        if (!isAuthenticated && (currentScreen == Screen.Symptoms || currentScreen == Screen.History || currentScreen == Screen.Settings)) {
                            currentScreen = Screen.SignUp
                        }
                        if (isAuthenticated && (currentScreen == Screen.Welcome || currentScreen == Screen.SignUp || currentScreen == Screen.Login)) {
                            // After auth, land on Symptoms
                            currentScreen = Screen.Symptoms
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                        AnimatedVisibility(
                            visible = currentScreen == Screen.Welcome,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            WelcomeScreen(
                                onContinue = { currentScreen = Screen.SignUp },
                                onLogin = { currentScreen = Screen.Login }
                            )
                        }

                        AnimatedVisibility(
                            visible = currentScreen != Screen.Welcome,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            when (currentScreen) {
                                Screen.SignUp -> SignUpScreen(onNavigate = { screen ->
                                    currentScreen = screen
                                })

                                Screen.Login -> LoginScreen(onNavigate = { screen ->
                                    currentScreen = screen
                                })
                                Screen.Symptoms -> SymptomCheckerApp(
                                    currentScreen = currentScreen,
                                    onScreenChange = { screen -> currentScreen = screen }
                                )

                                Screen.History -> HistoryScreen(onNavigate = { screen ->
                                    currentScreen = screen
                                })

                                Screen.Settings -> SettingsScreen(onNavigate = { screen ->
                                    currentScreen = screen
                                })

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryScreen(onNavigate: (Screen) -> Unit) {
        var searchQuery by remember { mutableStateOf("") }

        // Observe the mutable state list directly
        val historyState = SymptomHistoryManager.getHistoryState()
        val historyEntries by derivedStateOf {
            SymptomHistoryManager.searchEntries(searchQuery)
        }
        val dateFormat =
            remember { SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()) }
        val colors = LocalAppColors.current

        // Ensure sample data is loaded only once on app start
        LaunchedEffect(Unit) {
            // Only add sample data if this is the first time the app is running
            // and there are no entries at all (not just empty from clearing)
            if (SymptomHistoryManager.getAllEntries().isEmpty() && 
                !SymptomHistoryManager.hasBeenInitialized()) {
                SymptomHistoryManager.initializeWithSampleData()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Symptom History",
                    fontSize = scaledFontSize(26f).sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )
                                Text(
                    text = if (searchQuery.isBlank()) 
                        "${historyEntries.size} entries (${SymptomHistoryManager.getAllEntries().size} total)" 
                    else 
                        "${historyEntries.size} found",
                    fontSize = scaledFontSize(12f).sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Search Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = {
                                                    Text(
                            text = "Search previous symptoms...",
                            fontSize = scaledFontSize(14f).sp,
                            color = colors.textTertiary
                        )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = colors.primary,
                            unfocusedLabelColor = colors.textTertiary
                        )
                    )

                    // Debug button to add test entry
                    Button(
                        onClick = {
                            SymptomHistoryManager.addEntry(
                                SymptomHistoryEntry(
                                    symptoms = "Test symptom entry",
                                    aiResponse = "This is a test AI response for debugging purposes."
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Add Test Entry (Debug)",
                            fontSize = scaledFontSize(12f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // History List
            if (historyEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No history yet" else "No matching entries found",
                            fontSize = scaledFontSize(16f).sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Your symptom checks will appear here" else "Try a different search term",
                            fontSize = scaledFontSize(14f).sp,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        // Debug information
                        Text(
                            text = "Debug: Total entries = ${SymptomHistoryManager.getAllEntries().size}",
                            fontSize = scaledFontSize(12f).sp,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(historyEntries) { entry ->
                        HistoryEntryCard(entry = entry, dateFormat = dateFormat)
                    }
                }
            }

            // Bottom Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationItem(
                    icon = "🧪",
                    label = "Symptoms",
                    isSelected = false,
                    onClick = { onNavigate(Screen.Symptoms) }
                )
                NavigationItem(
                    icon = "📊",
                    label = "History",
                    isSelected = true,
                    onClick = { onNavigate(Screen.History) }
                )
                NavigationItem(
                    icon = "⚙️",
                    label = "Settings",
                    isSelected = false,
                    onClick = { onNavigate(Screen.Settings) }
                )
            }
        }
    }

    @Composable
    fun HistoryEntryCard(
        entry: SymptomHistoryEntry,
        dateFormat: SimpleDateFormat
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        val colors = LocalAppColors.current

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header with date and expand/collapse indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        fontSize = scaledFontSize(12f).sp,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isExpanded) "▼" else "▶",
                        fontSize = scaledFontSize(14f).sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Symptoms preview
                Text(
                    text = entry.symptoms,
                    fontSize = scaledFontSize(14f).sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2
                )

                // Expanded AI Response
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = colors.textTertiary.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "AI Analysis:",
                            fontSize = scaledFontSize(12f).sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = entry.aiResponse,
                            fontSize = scaledFontSize(13f).sp,
                            color = colors.textPrimary,
                            lineHeight = (scaledFontSize(18f)).sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(onNavigate: (Screen) -> Unit) {
        val context = LocalContext.current
        val isDarkMode by remember { derivedStateOf { ThemeManager.isDarkMode } }
        var notificationsEnabled by remember { mutableStateOf(true) }
        var languageSelection by remember { mutableStateOf("English") }
        val fontSize by remember { derivedStateOf { FontSizeManager.fontSize } }
        var editableName by remember { mutableStateOf(UserManager.currentUser?.name ?: "") }
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmNewPassword by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        val colors = ThemeManager.colors
        val settingsScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Settings",
                fontSize = scaledFontSize(26f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
            )

            // Scrollable content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(settingsScrollState)
            ) {
                // Appearance Settings Card
                Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Appearance",
                            fontSize = scaledFontSize(16f).sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Dark Mode Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dark Mode",
                                fontSize = scaledFontSize(14f).sp,
                                color = colors.textSecondary
                            )
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { 
                                    ThemeManager.isDarkMode = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.primary,
                                    checkedTrackColor = colors.primary.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Font Size Selection
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Font Size",
                                fontSize = scaledFontSize(14f).sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("Small", "Medium", "Large").forEach { size ->
                                    Text(
                                        text = size,
                                        fontSize = scaledFontSize(14f).sp,
                                        color = if (fontSize == size) colors.primary else colors.textSecondary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { FontSizeManager.fontSize = size }
                                            .background(
                                                if (fontSize == size) colors.primary.copy(alpha = 0.1f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Language and Region Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Language & Region",
                            fontSize = scaledFontSize(16f).sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Language",
                                fontSize = scaledFontSize(14f).sp,
                                color = colors.textSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    Toast.makeText(
                                        context,
                                        "More languages coming soon!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Text(
                                    text = languageSelection,
                                    fontSize = scaledFontSize(14f).sp,
                                    color = colors.primary
                                )
                                Text(
                                    text = " ▼",
                                    fontSize = scaledFontSize(12f).sp,
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }

                // Notifications Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Notifications",
                            fontSize = scaledFontSize(16f).sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Push Notifications",
                                    fontSize = scaledFontSize(14f).sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "Get important updates and reminders",
                                    fontSize = scaledFontSize(12f).sp,
                                    color = colors.textTertiary
                                )
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.primary,
                                    checkedTrackColor = colors.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // Profile and Password Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Profile",
                            fontSize = scaledFontSize(16f).sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                    OutlinedTextField(
                        value = editableName,
                        onValueChange = { editableName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Full Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (editableName.isBlank()) {
                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    try {
                                        FirebaseService.updateName(editableName)
                                        UserManager.updateName(editableName)
                                        Toast.makeText(context, "Name updated", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "Failed to update name", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White)
                    ) {
                        Text(
                            text = "Save Name",
                            fontSize = scaledFontSize(14f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Change Password",
                        fontSize = scaledFontSize(14f).sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        label = { Text("Current Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        label = { Text("New Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    Button(
                        onClick = {
                            val authUser = Firebase.auth.currentUser
                            when {
                                authUser == null -> Toast.makeText(context, "Not authenticated", Toast.LENGTH_SHORT).show()
                                newPassword.length < 6 -> Toast.makeText(context, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                newPassword != confirmNewPassword -> Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                else -> {
                                    scope.launch {
                                        try {
                                            val email = authUser.email ?: UserManager.currentUser?.email
                                            if (email.isNullOrBlank()) throw IllegalStateException("No email on account")
                                            val credential = EmailAuthProvider.getCredential(email, currentPassword)
                                            authUser.reauthenticate(credential).await()
                                            authUser.updatePassword(newPassword).await()
                                            currentPassword = ""
                                            newPassword = ""
                                            confirmNewPassword = ""
                                            Toast.makeText(context, "Password updated", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed to update password", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White)
                    ) {
                        Text(
                            text = "Save Password",
                            fontSize = scaledFontSize(14f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = colors.textTertiary.copy(alpha = 0.2f)
                    )

                    Button(
                        onClick = {
                            FirebaseService.logout()
                            UserManager.currentUser = null
                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                            onNavigate(Screen.Welcome)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Log Out",
                            fontSize = scaledFontSize(14f).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    }
                }
            }

            // Spacer no longer needed due to scrollable content

            // Bottom Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationItem(
                    icon = "🧪",
                    label = "Symptoms",
                    isSelected = false,
                    onClick = { onNavigate(Screen.Symptoms) }
                )
                NavigationItem(
                    icon = "📊",
                    label = "History",
                    isSelected = false,
                    onClick = { onNavigate(Screen.History) }
                )
                NavigationItem(
                    icon = "⚙️",
                    label = "Settings",
                    isSelected = true,
                    onClick = { onNavigate(Screen.Settings) }
                )
            }
        }
    }

    @Composable
    fun WelcomeScreen(onContinue: () -> Unit, onLogin: () -> Unit) {
        var isAnimated by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isAnimated) 1f else 0.8f,
            animationSpec = tween(1000, easing = EaseOutElastic)
        )

        LaunchedEffect(Unit) {
            isAnimated = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF6C63FF), Color(0xFFA084E8))
                    )
                )
        ) {
            // Background animated circles
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition()
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, delayMillis = index * 400),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .offset(
                            x = (100 * (index - 1)).dp,
                            y = (50 * offset).dp
                        )
                        .background(
                            Color.White.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                            Text(
                text = "Welcome to Symvora!",
                fontSize = scaledFontSize(36f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp),
                letterSpacing = (-1).sp
            )
            
            Text(
                text = "Your AI-powered symptom checker.",
                fontSize = scaledFontSize(18f).sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                modifier = Modifier.padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
            )

                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF6C63FF)
                    ),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = Color.Black.copy(alpha = 0.25f)
                        ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                                    Text(
                    text = "Get Started",
                    fontSize = scaledFontSize(17f).sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6C63FF),
                    letterSpacing = 0.5.sp
                )
                }

                // Login link
                Text(
                    text = "Already have an account? Log in",
                    color = Color.White,
                    fontSize = scaledFontSize(14f).sp,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { onLogin() }
                )
            }
        }
    }

    @Composable
    fun SignUpScreen(onNavigate: (Screen) -> Unit) {
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create your account",
                fontSize = scaledFontSize(26f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Full Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    Button(
                        onClick = {
                            val isEmailValid = email.contains("@") && email.contains(".")
                            when {
                                name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                                !isEmailValid -> Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                                password.length < 6 -> Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                password != confirmPassword -> Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                else -> {
                                    scope.launch {
                                        isSubmitting = true
                                        try {
                                            val user = FirebaseService.signUp(name, email, password)
                                            UserManager.currentUser = user
                                            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                                            onNavigate(Screen.Symptoms)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Signup failed", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Create Account",
                                fontSize = scaledFontSize(16f).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Text(
                text = "Already have an account? Log in",
                color = colors.primary,
                fontSize = scaledFontSize(14f).sp,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable { onNavigate(Screen.Login) }
            )
        }
    }

    @Composable
    fun LoginScreen(onNavigate: (Screen) -> Unit) {
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome back",
                fontSize = scaledFontSize(26f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        label = { Text("Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    Button(
                        onClick = {
                            val isEmailValid = email.contains("@") && email.contains(".")
                            when {
                                email.isBlank() || password.isBlank() ->
                                    Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                                !isEmailValid -> Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                                else -> {
                                    scope.launch {
                                        isSubmitting = true
                                        try {
                                            val user = FirebaseService.login(email, password)
                                            UserManager.currentUser = user
                                            Toast.makeText(context, "Logged in!", Toast.LENGTH_SHORT).show()
                                            onNavigate(Screen.Symptoms)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Log In",
                                fontSize = scaledFontSize(16f).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Text(
                text = "Don't have an account? Sign up",
                color = colors.primary,
                fontSize = scaledFontSize(14f).sp,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable { onNavigate(Screen.SignUp) }
            )
        }
    }

    @Composable
    fun SymptomCheckerApp(
        currentScreen: Screen,
        onScreenChange: (Screen) -> Unit
    ) {
        var symptomText by remember { mutableStateOf("") }
        var resultText by remember { mutableStateOf("AI Response will appear here...") }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val colors = LocalAppColors.current
        val userName = UserManager.currentUser?.name ?: ""

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Greeting and title
            if (userName.isNotBlank()) {
                Text(
                    text = "Hi, $userName",
                    fontSize = scaledFontSize(20f).sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            Text(
                text = "Symptom Checker AI",
                fontSize = scaledFontSize(26f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // 2. Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                                    Text(
                    text = "Describe your symptoms",
                    fontSize = scaledFontSize(16f).sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                    OutlinedTextField(
                        value = symptomText,
                        onValueChange = { symptomText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = scaledFontSize(14f).sp,
                        color = colors.textPrimary
                    ),
                        minLines = 4,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = colors.primary,
                            unfocusedLabelColor = colors.textTertiary
                        )
                    )
                }
            }

            // 3. Gradient Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = {
                        if (symptomText.isNotBlank()) {
                            isLoading = true
                            resultText = "Analyzing symptoms..."

                            CoroutineScope(Dispatchers.IO).launch { // Use Dispatchers.IO for network operations
                                try {
                                    val aiResponse = DeepseekApi.getDiagnosis(symptomText)
                                    with(Dispatchers.Main) { // Switch back to Main for UI updates
                                        resultText = aiResponse
                                    }
                                } catch (e: Exception) {
                                    with(Dispatchers.Main) {
                                        resultText = "Error: ${e.message}"
                                        Toast.makeText(context, "Error analyzing symptoms: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                                finally {
                                    with(Dispatchers.Main) {
                                        isLoading = false
                                    }
                                }
                            }

                            // Save to history
                            SymptomHistoryManager.addEntry(
                                SymptomHistoryEntry(
                                    symptoms = symptomText,
                                    aiResponse = resultText // Use resultText which now holds AI response or error
                                )
                            )

                        } else {
                            Toast.makeText(
                                context,
                                "Please enter your symptoms first.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = colors.primary.copy(alpha = 0.25f)
                        ),
                    enabled = symptomText.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White,
                        disabledContainerColor = colors.primary.copy(alpha = 0.6f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                                            Text(
                        text = "Analyze Symptoms",
                        fontSize = scaledFontSize(17f).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = Color.White
                    )
                    }
                }
            }

            // 4. AI Response Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                val responseScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(responseScrollState)
                ) {
                                    Text(
                    text = "AI Response",
                    fontSize = scaledFontSize(16f).sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                    Text(
                    text = resultText,
                    fontSize = scaledFontSize(14f).sp,
                    fontWeight = FontWeight.Light,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                )
                }
            }

                    // 5. Subtle Disclaimer
        Text(
            text = "Disclaimer: This app is not a medical diagnosis tool. Always consult a healthcare professional.",
            fontSize = scaledFontSize(11f).sp,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            modifier = Modifier.padding(bottom = 16.dp)
        )

            // 6. Bottom Navigation (Optional Enhancement)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationItem(
                    icon = "🧪",
                    label = "Symptoms",
                    isSelected = currentScreen == Screen.Symptoms,
                    onClick = { onScreenChange(Screen.Symptoms) }
                )

                NavigationItem(
                    icon = "📊",
                    label = "History",
                    isSelected = currentScreen == Screen.History,
                    onClick = { onScreenChange(Screen.History) }
                )

                NavigationItem(
                    icon = "⚙️",
                    label = "Settings",
                    isSelected = currentScreen == Screen.Settings,
                    onClick = { onScreenChange(Screen.Settings) }
                )
            }
        }
    }

    @Composable
    fun NavigationItem(
        icon: String,
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.1f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        val colors = LocalAppColors.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .scale(scale)
        ) {
                    Text(
            text = icon,
            fontSize = scaledFontSize(24f).sp
        )
        Text(
            text = label,
            fontSize = scaledFontSize(12f).sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) colors.primary else colors.textTertiary
        )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun SymptomCheckerAppPreview() {
        SymvoraTheme {
            SymptomCheckerApp(
                currentScreen = Screen.Symptoms,
                onScreenChange = {}
            )
        }
    }

}