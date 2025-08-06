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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

enum class Screen {
    Welcome, Symptoms, History, Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SymvoraTheme {
                var currentScreen by remember { mutableStateOf(Screen.Welcome) }
                
                Box(modifier = Modifier.fillMaxSize()) {
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
                                onScreenChange = { currentScreen = it }
                            )
                            Screen.History -> HistoryScreen(onNavigate = { currentScreen = it })
                            Screen.Settings -> SettingsScreen(onNavigate = { currentScreen = it })
                            else -> Unit
                        }
                    }
                }
            }
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
        Button(
            onClick = {
                if (symptomText.isNotBlank()) {
                    isLoading = true
                    resultText = "Analyzing symptoms..."
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2000) // 2 second delay
                        
                        resultText = """
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
                        
                        isLoading = false
                    }
                } else {
                    Toast.makeText(context, "Please enter your symptoms first.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 24.dp),
            enabled = symptomText.isNotBlank() && !isLoading,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = Color(0xFF6C63FF).copy(alpha = 0.25f)
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6C63FF),
                                Color(0xFF8A7FFF)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Analyze Symptoms",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
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
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color(0xFF6C63FF) else Color(0xFF999999)
        )
    }
}

@Composable
fun HistoryScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Symptom History",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E2E2E),
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
        )

        Text(
            text = "Your previous symptom checks will appear here.",
            fontSize = 16.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

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
fun SettingsScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E2E2E),
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF9F9F9)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Version",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E2E2E)
                )
                Text(
                    text = "1.0.0",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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