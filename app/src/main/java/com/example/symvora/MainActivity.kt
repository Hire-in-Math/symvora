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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

// Theme Configuration
object ThemeConfig {
    var isDarkMode by mutableStateOf(false)
        private set
    
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

    fun toggleTheme() {
        isDarkMode = !isDarkMode
    }
}

// Data model for symptom history
data class SymptomHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val symptoms: String,
    val aiResponse: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Global history storage
object SymptomHistoryManager {
    private val _history = mutableStateListOf<SymptomHistoryEntry>()
    
    init {
        // Add some sample data for testing
        addSampleData()
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
    Welcome, Symptoms, History, Settings
}

// Theme manager
object ThemeManager {
    var isDarkMode by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode = ThemeManager.isDarkMode

            val colors = if (isDarkMode) {
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

            CompositionLocalProvider(LocalAppColors provides colors) {
                SymvoraTheme {
                    var currentScreen by remember { mutableStateOf(Screen.Welcome) }

                    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                        AnimatedVisibility(
                            visible = currentScreen == Screen.Welcome,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            WelcomeScreen(onContinue = { currentScreen = Screen.Symptoms })
                        }

                        AnimatedVisibility(
                            visible = currentScreen != Screen.Welcome,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            when (currentScreen) {
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

        // Ensure sample data is loaded
        LaunchedEffect(Unit) {
            if (SymptomHistoryManager.getAllEntries().isEmpty()) {
                // Add sample data if empty
                SymptomHistoryManager.addEntry(
                    SymptomHistoryEntry(
                        symptoms = "Sample headache and fever",
                        aiResponse = "This is a sample AI response for testing the history feature."
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
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
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = if (searchQuery.isBlank())
                        "${historyEntries.size} entries (${SymptomHistoryManager.getAllEntries().size} total)"
                    else
                        "${historyEntries.size} found",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
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
                    containerColor = Color(0xFFF9F9F9)
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
                                fontSize = 14.sp,
                                color = Color(0xFF999999)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFF6C63FF),
                            unfocusedLabelColor = Color(0xFF999999)
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
                            containerColor = Color(0xFF6C63FF),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Add Test Entry (Debug)",
                            fontSize = 12.sp,
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
                            fontSize = 16.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Your symptom checks will appear here" else "Try a different search term",
                            fontSize = 14.sp,
                            color = Color(0xFF999999),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        // Debug information
                        Text(
                            text = "Debug: Total entries = ${SymptomHistoryManager.getAllEntries().size}",
                            fontSize = 12.sp,
                            color = Color(0xFF999999),
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
                        color = Color(0xFFF9F9F9),
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
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
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isExpanded) "▼" else "▶",
                        fontSize = 14.sp,
                        color = Color(0xFF6C63FF),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Symptoms preview
                Text(
                    text = entry.symptoms,
                    fontSize = 14.sp,
                    color = Color(0xFF2E2E2E),
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
                            color = Color(0xFFE0E0E0)
                        )
                        Text(
                            text = "AI Analysis:",
                            fontSize = 12.sp,
                            color = Color(0xFF6C63FF),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = entry.aiResponse,
                            fontSize = 13.sp,
                            color = Color(0xFF2E2E2E),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(onNavigate: (Screen) -> Unit) {
        val context = LocalContext.current
        var isDarkMode by remember { mutableStateOf(ThemeManager.isDarkMode) }
        var notificationsEnabled by remember { mutableStateOf(true) }
        var languageSelection by remember { mutableStateOf("English") }
        var fontSize by remember { mutableStateOf("Medium") }

        val colors = if (isDarkMode) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Settings",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
            )

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
                        fontSize = 16.sp,
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
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = {
                                isDarkMode = it
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
                            fontSize = 14.sp,
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
                                    fontSize = 14.sp,
                                    color = if (fontSize == size) colors.primary else colors.textSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { fontSize = size }
                                        .background(
                                            if (fontSize == size) Color(0xFF6C63FF).copy(alpha = 0.1f)
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
                        fontSize = 16.sp,
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
                            fontSize = 14.sp,
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
                                fontSize = 14.sp,
                                color = colors.primary
                            )
                            Text(
                                text = " ▼",
                                fontSize = 12.sp,
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
                        fontSize = 16.sp,
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
                                fontSize = 14.sp,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "Get important updates and reminders",
                                fontSize = 12.sp,
                                color = colors.textTertiary
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF6C63FF),
                                checkedTrackColor = Color(0xFF6C63FF).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // App Version Card
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
                        text = "Data Management",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = {
                            SymptomHistoryManager.clearHistory()
                            Toast.makeText(
                                context,
                                "History cleared successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Clear Symptom History",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) { }

            // Bottom Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF9F9F9),
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
    fun WelcomeScreen(onContinue: () -> Unit) {
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
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp),
                    letterSpacing = (-1).sp
                )

                Text(
                    text = "Your AI-powered symptom checker.",
                    fontSize = 18.sp,
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6C63FF),
                        letterSpacing = 0.5.sp
                    )
                }
            }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header Bar
            Text(
                text = "Symptom Checker AI",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E2E2E),
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
            )

            // 2. Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF9F9F9)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Describe your symptoms",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E2E2E),
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
                            fontSize = 14.sp,
                            color = Color(0xFF2E2E2E)
                        ),
                        minLines = 4,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedLabelColor = Color(0xFF6C63FF),
                            unfocusedLabelColor = Color(0xFF999999)
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

                            CoroutineScope(Dispatchers.Main).launch {
                                delay(2000) // 2 second delay

                                val aiResponse = """
                                Based on your symptoms, here are some general possibilities:
                                
                                Possible Conditions:
                                • Common cold or flu
                                • Seasonal allergies
                                • Stress-related symptoms
                                
                                General Advice:
                                • Rest and stay hydrated
                                • Monitor your symptoms
                                • Avoid self-diagnosis
                                
                                ⚠️ IMPORTANT: This is for informational purposes only. 
                                Always consult a healthcare professional for proper diagnosis and treatment.
                            """.trimIndent()

                                resultText = aiResponse

                                // Save to history
                                SymptomHistoryManager.addEntry(
                                    SymptomHistoryEntry(
                                        symptoms = symptomText,
                                        aiResponse = aiResponse
                                    )
                                )

                                isLoading = false
                            }
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
                            spotColor = Color(0xFF6C63FF).copy(alpha = 0.25f)
                        ),
                    enabled = symptomText.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C63FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6C63FF).copy(alpha = 0.6f),
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
                            fontSize = 17.sp,
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
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "AI Response",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E2E2E),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = resultText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF2E2E2E),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            // 5. Subtle Disclaimer
            Text(
                text = "Disclaimer: This app is not a medical diagnosis tool. Always consult a healthcare professional.",
                fontSize = 11.sp,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 6. Bottom Navigation (Optional Enhancement)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF9F9F9),
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .scale(scale)
        ) {
            Text(
                text = icon,
                fontSize = 24.sp
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isSelected) Color(0xFF6C63FF) else Color(0xFF999999)
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