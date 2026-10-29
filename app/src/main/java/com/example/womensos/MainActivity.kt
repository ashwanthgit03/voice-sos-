package com.example.womensos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── Brand colours ──────────────────────────────────────────────────────────
private val Ink   = Color(0xFF241B2F)
private val Rose  = Color(0xFFDA315D)
private val Pale  = Color(0xFFFFF4F6)
private val Muted = Color(0xFF766D7D)
private val Green = Color(0xFF388E3C)
private val Amber = Color(0xFFE65100)

private val countryCodes = listOf("+91", "+1", "+44", "+61", "+971", "+94", "+65")

// ── Activity ───────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WomenSosApp() }
    }
}

// ── Root composable ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomenSosApp() {
    val context       = LocalContext.current
    val firebaseReady = remember { FirebaseApp.initializeApp(context) != null }
    val auth   = remember(firebaseReady) { if (firebaseReady) FirebaseAuth.getInstance() else null }
    val db     = remember(firebaseReady) { if (firebaseReady) FirebaseFirestore.getInstance() else null }

    // ── Auth / UI state ────────────────────────────────────────────────────
    var screen  by remember { mutableStateOf(if (auth?.currentUser == null) "welcome" else "loading") }
    var message by remember { mutableStateOf(if (firebaseReady) "" else "Firebase config missing — check google-services.json.") }
    var busy    by remember { mutableStateOf(false) }

    // Profile fields
    var fullName      by remember { mutableStateOf("") }
    var countryCode   by remember { mutableStateOf("+91") }
    var phone         by remember { mutableStateOf("") }
    var email         by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }

    // Emergency contacts
    var contact1Code by remember { mutableStateOf("+91") }
    var contact2Code by remember { mutableStateOf("+91") }
    var contact3Code by remember { mutableStateOf("+91") }
    var contact1     by remember { mutableStateOf("") }
    var contact2     by remember { mutableStateOf("") }
    var contact3     by remember { mutableStateOf("") }
    var emergencyEmail by remember { mutableStateOf("") }

    // Address
    var address      by remember { mutableStateOf("") }
    var place        by remember { mutableStateOf("") }
    var pincode      by remember { mutableStateOf("") }
    var locationText by remember { mutableStateOf("Location not fetched") }

    var monitoring            by remember { mutableStateOf(false) }
    var reauthPassword        by remember { mutableStateOf("") }
    var pendingProtectedScreen by remember { mutableStateOf("profile") }
    var editingExistingProfile by remember { mutableStateOf(false) }

    // ── Backend + demo state ───────────────────────────────────────────────
    val scope      = rememberCoroutineScope()
    val prefs      = remember { context.getSharedPreferences("kavalan_prefs", Context.MODE_PRIVATE) }
    var backendUrl by remember {
        mutableStateOf(prefs.getString("backend_url", "http://192.168.1.100:8000") ?: "http://192.168.1.100:8000")
    }
    var uploadStatus by remember { mutableStateOf("") }
    var isUploading  by remember { mutableStateOf(false) }
    // Observe the service's live state
    var serviceResult  by remember { mutableStateOf<PredictionResult?>(null) }
    var serviceRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { AudioMonitoringService.lastResult.collect { serviceResult  = it } }
        launch { AudioMonitoringService.isRunning.collect  { serviceRunning = it } }
    }

    // ── Firestore profile loader ───────────────────────────────────────────
    fun loadProfileAndRoute(userId: String) {
        if (db == null) { screen = "welcome"; message = "Firebase is not initialised."; return }
        busy = true
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                fullName      = doc.getString("fullName").orEmpty()
                countryCode   = doc.getString("countryCode") ?: "+91"
                phone         = doc.getString("phoneNumber").orEmpty()
                email         = doc.getString("email") ?: (auth?.currentUser?.email.orEmpty())
                contact1Code  = doc.getString("contact1CountryCode") ?: "+91"
                contact2Code  = doc.getString("contact2CountryCode") ?: "+91"
                contact3Code  = doc.getString("contact3CountryCode") ?: "+91"
                contact1      = doc.getString("contact1").orEmpty()
                contact2      = doc.getString("contact2").orEmpty()
                contact3      = doc.getString("contact3").orEmpty()
                emergencyEmail = doc.getString("emergencyEmail").orEmpty()
                address       = doc.getString("address").orEmpty()
                place         = doc.getString("place").orEmpty()
                pincode       = doc.getString("pincode").orEmpty()
                val complete  = doc.getBoolean("setupCompleted") == true
                val step      = doc.getString("setupStep") ?: "setupContacts"
                screen        = if (complete) "home" else step
                busy          = false
            }
            .addOnFailureListener {
                busy   = false
                screen = "setupContacts"
                message = "Could not load saved setup: ${it.localizedMessage ?: "unknown error"}"
            }
    }

    // ── Permission launchers ───────────────────────────────────────────────

    // Multi-permission: mic + location
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        val locGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                         results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (micGranted) {
            startWomenSOSMonitoring(context, backendUrl)
            monitoring = true
        }
        message = "Microphone: ${if (micGranted) "✓ granted" else "✗ denied"}  •  Location: ${if (locGranted) "✓ granted" else "✗ denied"}"
    }

    // Single-permission: just mic (fallback for the toggle)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startWomenSOSMonitoring(context, backendUrl)
            monitoring = true
            message = "Kavalan is now listening for distress signals."
        } else {
            monitoring = false
            message = "Microphone permission denied — monitoring is off."
        }
    }

    // Demo file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isUploading  = true
        uploadStatus = "Reading audio file…"

        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null || bytes.isEmpty()) {
                    uploadStatus = "Could not read the selected file."
                    isUploading  = false
                    return@launch
                }
                val filename = uri.lastPathSegment ?: "audio.wav"
                uploadStatus = "Sending ${bytes.size / 1024} KB to AI model…"

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val body = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "audio", filename,
                                bytes.toRequestBody("audio/*".toMediaType())
                            )
                            .build()
                        val request = Request.Builder()
                            .url("${backendUrl.trimEnd('/')}/predict")
                            .post(body)
                            .build()
                        ApiClient.http.newCall(request).execute()
                            .use { it.body?.string() ?: "" }
                    }
                }

                result.onSuccess { body ->
                    val json      = JSONObject(body)
                    val pred      = json.optString("prediction", "unknown")
                    val conf      = json.optDouble("confidence", 0.0)
                    val whisper   = json.optString("whisper_text", "")
                    val kwFound   = json.optBoolean("keyword_found", false)
                    val emergency = json.optBoolean("trigger_emergency", false)

                    uploadStatus = buildString {
                        appendLine("📊 Prediction : $pred")
                        appendLine("📈 Confidence : ${(conf * 100).toInt()}%")
                        if (whisper.isNotBlank()) appendLine("🎙 Heard       : \"$whisper\"")
                        if (kwFound) appendLine("⚠️  Distress keyword detected!")
                        append(if (emergency) "🚨 EMERGENCY TRIGGERED — beep playing!" else "✅ No emergency signal detected.")
                    }

                    if (emergency) playBeep(context)

                }.onFailure { e ->
                    uploadStatus = "❌ Error: ${e.message}\n\nCheck that the backend is running and the URL is correct."
                }

            } catch (e: Exception) {
                uploadStatus = "❌ Error: ${e.message}"
            } finally {
                isUploading = false
            }
        }
    }

    // ── Firebase routing ───────────────────────────────────────────────────
    LaunchedEffect(firebaseReady, auth?.currentUser?.uid) {
        val user = auth?.currentUser
        if (firebaseReady && user != null) {
            loadProfileAndRoute(user.uid)
        } else if (user == null && screen == "loading") {
            screen = "welcome"
        }
    }

    // ── Request mic + location on first launch ─────────────────────────────
    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    // ── Profile saver ──────────────────────────────────────────────────────
    fun saveProfile(completed: Boolean, nextStep: String, onSaved: () -> Unit) {
        val user = auth?.currentUser
        if (user == null || db == null) {
            message = "Please sign in before saving profile information."
            return
        }
        val data = hashMapOf<String, Any>(
            "fullName"             to fullName.trim(),
            "countryCode"          to countryCode,
            "phoneNumber"          to phone.filter(Char::isDigit),
            "email"                to (user.email ?: email.trim()),
            "contact1CountryCode"  to contact1Code,
            "contact2CountryCode"  to contact2Code,
            "contact3CountryCode"  to contact3Code,
            "contact1"             to contact1.filter(Char::isDigit),
            "contact2"             to contact2.filter(Char::isDigit),
            "contact3"             to contact3.filter(Char::isDigit),
            "emergencyEmail"       to emergencyEmail.trim(),
            "address"              to address.trim(),
            "place"                to place.trim(),
            "pincode"              to pincode.filter(Char::isDigit),
            "setupCompleted"       to completed,
            "setupStep"            to nextStep,
            "updatedAt"            to System.currentTimeMillis()
        )
        busy = true
        db.collection("users").document(user.uid).set(data)
            .addOnSuccessListener { busy = false; message = "Information saved."; onSaved() }
            .addOnFailureListener { busy = false; message = "Could not save: ${it.localizedMessage ?: "unknown error"}" }
    }

    // ── Location helper ────────────────────────────────────────────────────
    fun getLocation() {
        val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    locationText = if (loc != null) "${loc.latitude}, ${loc.longitude}"
                                   else "No recent location. Enable device GPS and retry."
                }
                .addOnFailureListener { locationText = "Location unavailable: ${it.localizedMessage}" }
        } catch (_: SecurityException) {
            locationText = "Location permission required."
        }
    }

    fun validPhone(number: String) = number.length == 10 && number.all(Char::isDigit)

    // ── Theme + Scaffold ───────────────────────────────────────────────────
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary   = Rose,
            onPrimary = Color.White,
            background = Pale,
            surface   = Color.White
        )
    ) {
        val authenticated = auth?.currentUser != null
        val authScreens   = screen in listOf("welcome", "login", "register", "loading")

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("WOMEN SOS", fontWeight = FontWeight.ExtraBold, color = Ink)
                    },
                    navigationIcon = {
                        if (authenticated && !authScreens &&
                            screen !in listOf("setupContacts", "setupAddress", "reauth")) {
                            IconButton(onClick = { screen = if (screen == "menu") "home" else "menu" }) {
                                Icon(Icons.Default.Menu, contentDescription = "Navigation")
                            }
                        }
                    },
                    actions = {
                        Icon(
                            Icons.Default.Shield, contentDescription = "Safety",
                            tint = Rose, modifier = Modifier.padding(end = 16.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Pale)
                )
            },
            bottomBar = {
                if (authenticated && screen in listOf("home", "history", "profile")) {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = screen == "home",
                            onClick  = { screen = "home" },
                            icon     = { Icon(Icons.Default.Home, null) },
                            label    = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = screen == "history",
                            onClick  = { screen = "history" },
                            icon     = { Icon(Icons.Default.History, null) },
                            label    = { Text("History") }
                        )
                        NavigationBarItem(
                            selected = screen == "profile",
                            onClick  = {
                                pendingProtectedScreen = "profile"
                                editingExistingProfile = true
                                reauthPassword = ""
                                screen = "reauth"
                            },
                            icon  = { Icon(Icons.Default.Person, null) },
                            label = { Text("Profile") }
                        )
                    }
                }
            },
            containerColor = Pale
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (screen) {

                    // ── Loading ──────────────────────────────────────────
                    "loading" -> {
                        CircularProgressIndicator(color = Rose)
                        Text("Loading your account…", color = Muted)
                    }

                    // ── Welcome ──────────────────────────────────────────
                    "welcome" -> {
                        Spacer(Modifier.height(32.dp))
                        Icon(Icons.Default.Shield, null, tint = Rose, modifier = Modifier.size(76.dp))
                        Text(
                            "Your safety matters.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = Ink
                        )
                        Text(
                            "A personal safety companion with AI distress detection and SOS alerts.",
                            color = Muted
                        )
                        Spacer(Modifier.height(28.dp))
                        PrimaryButton("Create account") { message = ""; screen = "register" }
                        OutlinedButton(
                            onClick = { message = ""; screen = "login" },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("I already have an account") }
                        StatusNote(message)
                    }

                    // ── Register ─────────────────────────────────────────
                    "register" -> {
                        SectionTitle("Create your account • 1 of 3")
                        Text("Enter your personal details to get started.", color = Muted)
                        Field("Username / full name", fullName, { fullName = it })
                        PhoneField("Phone number", countryCode, { countryCode = it }, phone, { phone = it })
                        Field("Email address", email, { email = it })
                        Field("Password (minimum 6 characters)", password, { password = it }, password = true)
                        PrimaryButton(if (busy) "Please wait…" else "Next") {
                            if (busy) return@PrimaryButton
                            when {
                                fullName.isBlank() -> message = "Enter your name."
                                !validPhone(phone) -> message = "Enter exactly 10 digits for your phone number."
                                !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> message = "Enter a valid email address."
                                password.length < 6 -> message = "Password must contain at least 6 characters."
                                auth == null || db == null -> message = "Firebase is not configured. Check google-services.json."
                                else -> {
                                    busy = true
                                    auth.createUserWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener { result ->
                                            val user = result.user
                                            user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(fullName.trim()).build())
                                            user?.sendEmailVerification()
                                            val uid = user?.uid
                                            if (uid == null) {
                                                busy = false
                                                message = "Account created but user ID unavailable. Please sign in."
                                            } else {
                                                val draft = hashMapOf<String, Any>(
                                                    "fullName" to fullName.trim(),
                                                    "countryCode" to countryCode,
                                                    "phoneNumber" to phone,
                                                    "email" to email.trim(),
                                                    "setupCompleted" to false,
                                                    "setupStep" to "setupContacts",
                                                    "createdAt" to System.currentTimeMillis(),
                                                    "updatedAt" to System.currentTimeMillis()
                                                )
                                                db.collection("users").document(uid).set(draft)
                                                    .addOnSuccessListener {
                                                        busy = false; password = ""
                                                        message = "Account created. A verification email was sent."
                                                        screen = "setupContacts"
                                                    }
                                                    .addOnFailureListener {
                                                        busy = false
                                                        message = "Account created, but setup could not be saved: ${it.localizedMessage}"
                                                        screen = "setupContacts"
                                                    }
                                            }
                                        }
                                        .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Registration failed." }
                                }
                            }
                        }
                        TextButton(onClick = { message = ""; screen = "login" }) { Text("Already have an account? Sign in") }
                        StatusNote(message)
                    }

                    // ── Login ────────────────────────────────────────────
                    "login" -> {
                        SectionTitle("Welcome back")
                        Text("Sign in to open your safety home screen.", color = Muted)
                        Field("Email address", email, { email = it })
                        Field("Password", password, { password = it }, password = true)
                        PrimaryButton(if (busy) "Signing in…" else "Sign in") {
                            if (busy) return@PrimaryButton
                            when {
                                email.isBlank() || password.isBlank() -> message = "Enter your email and password."
                                auth == null -> message = "Firebase is not configured."
                                else -> {
                                    busy = true
                                    auth.signInWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener { result ->
                                            password = ""; busy = false
                                            result.user?.uid?.let { loadProfileAndRoute(it) }
                                                ?: run { screen = "welcome"; message = "Could not read account." }
                                        }
                                        .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Login failed." }
                                }
                            }
                        }
                        TextButton(onClick = { message = ""; screen = "register" }) { Text("New here? Create an account") }
                        StatusNote(message)
                    }

                    // ── Setup: Contacts ──────────────────────────────────
                    "setupContacts" -> {
                        SectionTitle("Emergency contacts • 2 of 3")
                        Text("Add three trusted people and an emergency email address.", color = Muted)
                        PhoneField("Trusted contact 1", contact1Code, { contact1Code = it }, contact1, { contact1 = it })
                        PhoneField("Trusted contact 2", contact2Code, { contact2Code = it }, contact2, { contact2 = it })
                        PhoneField("Trusted contact 3", contact3Code, { contact3Code = it }, contact3, { contact3 = it })
                        Field("Emergency email address", emergencyEmail, { emergencyEmail = it })
                        PrimaryButton(if (busy) "Saving…" else "Next") {
                            if (busy) return@PrimaryButton
                            if (!validPhone(contact1) || !validPhone(contact2) || !validPhone(contact3)) {
                                message = "Enter exactly 10 digits for all three contact phone numbers."
                            } else if (emergencyEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emergencyEmail.trim()).matches()) {
                                message = "Enter a valid emergency email or leave it blank."
                            } else {
                                saveProfile(completed = false, nextStep = "setupAddress") { screen = "setupAddress" }
                            }
                        }
                        TextButton(onClick = { screen = "register" }) { Text("Back") }
                        StatusNote(message)
                    }

                    // ── Setup: Address ───────────────────────────────────
                    "setupAddress" -> {
                        SectionTitle("Your address • 3 of 3")
                        Text("Add your address so it can be included in your profile.", color = Muted)
                        Field("Address", address, { address = it })
                        Field("Place / city", place, { place = it })
                        Field("PIN code", pincode, { pincode = it.filter(Char::isDigit).take(6) })
                        PrimaryButton(if (busy) "Saving…" else "Get Started") {
                            if (busy) return@PrimaryButton
                            when {
                                address.isBlank() -> message = "Enter your address."
                                place.isBlank()   -> message = "Enter your place or city."
                                pincode.length != 6 || !pincode.all(Char::isDigit) -> message = "Enter a 6-digit PIN code."
                                else -> saveProfile(completed = true, nextStep = "home") {
                                    message = "Setup complete. Stay safe."
                                    screen  = "home"
                                }
                            }
                        }
                        TextButton(onClick = { screen = "setupContacts" }) { Text("Back") }
                        StatusNote(message)
                    }

                    // ── Re-auth guard ────────────────────────────────────
                    "reauth" -> {
                        SectionTitle("Verify it's you")
                        Text("Enter your current password before changing saved details.", color = Muted)
                        Field("Current password", reauthPassword, { reauthPassword = it }, password = true)
                        PrimaryButton(if (busy) "Verifying…" else "Continue") {
                            val user = auth?.currentUser
                            if (user?.email.isNullOrBlank() || reauthPassword.isBlank()) {
                                message = "Enter your current password."
                            } else {
                                busy = true
                                val credential = EmailAuthProvider.getCredential(user!!.email!!, reauthPassword)
                                user.reauthenticate(credential)
                                    .addOnSuccessListener { busy = false; reauthPassword = ""; screen = pendingProtectedScreen }
                                    .addOnFailureListener { busy = false; message = "Verification failed: ${it.localizedMessage ?: "try again"}" }
                            }
                        }
                        TextButton(onClick = { screen = "home" }) { Text("Cancel") }
                        StatusNote(message)
                    }

                    // ── Profile editor ───────────────────────────────────
                    "profile" -> {
                        SectionTitle("Edit profile")
                        Text("Your password was verified before opening this screen.", color = Muted)
                        Field("Full name", fullName, { fullName = it })
                        PhoneField("Phone number", countryCode, { countryCode = it }, phone, { phone = it })
                        Field("Email address", email, { email = it })
                        Field("Address", address, { address = it })
                        Field("Place / city", place, { place = it })
                        Field("PIN code", pincode, { pincode = it.filter(Char::isDigit).take(6) })
                        PrimaryButton(if (busy) "Saving…" else "Save changes") {
                            when {
                                fullName.isBlank() -> message = "Enter your name."
                                !validPhone(phone) -> message = "Enter exactly 10 digits for phone."
                                pincode.isNotBlank() && (pincode.length != 6 || !pincode.all(Char::isDigit)) -> message = "PIN code must be 6 digits."
                                else -> saveProfile(completed = true, nextStep = "home") { screen = "home" }
                            }
                        }
                        OutlinedButton(onClick = {
                            pendingProtectedScreen = "contacts"; reauthPassword = ""; screen = "reauth"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Edit emergency contacts") }
                        StatusNote(message)
                    }

                    // ── Contacts editor ──────────────────────────────────
                    "contacts" -> {
                        SectionTitle("Edit emergency contacts")
                        PhoneField("Trusted contact 1", contact1Code, { contact1Code = it }, contact1, { contact1 = it })
                        PhoneField("Trusted contact 2", contact2Code, { contact2Code = it }, contact2, { contact2 = it })
                        PhoneField("Trusted contact 3", contact3Code, { contact3Code = it }, contact3, { contact3 = it })
                        Field("Emergency email address", emergencyEmail, { emergencyEmail = it })
                        PrimaryButton(if (busy) "Saving…" else "Save contacts") {
                            if (!validPhone(contact1) || !validPhone(contact2) || !validPhone(contact3)) {
                                message = "Enter exactly 10 digits for all three contacts."
                            } else if (emergencyEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emergencyEmail.trim()).matches()) {
                                message = "Enter a valid emergency email."
                            } else saveProfile(completed = true, nextStep = "home") { screen = "profile" }
                        }
                        TextButton(onClick = { screen = "profile" }) { Text("Back to profile") }
                        StatusNote(message)
                    }

                    // ── HOME ─────────────────────────────────────────────
                    "home" -> {
                        if (auth?.currentUser == null) {
                            LaunchedEffect(Unit) { screen = "welcome" }
                        } else {

                            // Auto-start live monitoring as soon as home screen loads
                            LaunchedEffect(Unit) {
                                val micGranted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (micGranted && !serviceRunning) {
                                    startWomenSOSMonitoring(context, backendUrl)
                                    monitoring = true
                                } else if (!micGranted) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }

                            // ── Greeting ───────────────────────────────────────────
                            Text(
                                "Hello${if (fullName.isNotBlank()) ", $fullName" else ""}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, color = Ink
                            )
                            Text("Kavalan is watching over you.", color = Muted)
                            Spacer(Modifier.height(16.dp))

                            // ── Backend URL card ───────────────────────────────────
                            Card(
                                shape  = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Wifi, null, tint = Rose)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Backend Server URL", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = backendUrl,
                                        onValueChange = { backendUrl = it },
                                        label = { Text("e.g. http://192.168.1.5:8000") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            prefs.edit().putString("backend_url", backendUrl).apply()
                                            // Restart service with new URL
                                            stopWomenSOSMonitoring(context)
                                            startWomenSOSMonitoring(context, backendUrl)
                                            monitoring = true
                                            message    = "Backend URL saved. Monitoring restarted."
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Save & Reconnect") }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // ── Live monitoring status card ────────────────────────
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (serviceRunning) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                                            null,
                                            tint = if (serviceRunning) Green else Amber
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (serviceRunning) "🟢 Live Monitoring Active" else "🔴 Monitoring Off",
                                                fontWeight = FontWeight.Bold,
                                                color = if (serviceRunning) Green else Amber
                                            )
                                            Text(
                                                if (serviceRunning) "Recording & uploading 10-second windows to AI model"
                                                else "Grant mic permission and tap Save & Reconnect",
                                                color = Muted,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        // Manual toggle
                                        Switch(
                                            checked  = serviceRunning,
                                            onCheckedChange = { on ->
                                                if (on) {
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        context, Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        startWomenSOSMonitoring(context, backendUrl)
                                                        monitoring = true
                                                        message    = "Live monitoring started."
                                                    } else {
                                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                } else {
                                                    stopWomenSOSMonitoring(context)
                                                    monitoring = false
                                                    message    = "Live monitoring stopped."
                                                }
                                            }
                                        )
                                    }

                                    // Last AI result
                                    serviceResult?.let { res ->
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                        Text(
                                            "Last AI Result",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Prediction: ${res.prediction}  •  Confidence: ${(res.distressConfidence * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (res.whisperText.isNotBlank()) {
                                            Text(
                                                "🎙 Heard: \"${res.whisperText}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Muted
                                            )
                                        }
                                        if (res.triggerEmergency) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "🚨 EMERGENCY TRIGGERED",
                                                color = Rose,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (res.error.isNotBlank()) {
                                            Text(
                                                "⚠ ${res.error}",
                                                color = Amber,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // ── Demo: Upload audio file ────────────────────────────
                            Card(
                                shape  = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Upload, null, tint = Rose)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("Demo — Upload Audio File", fontWeight = FontWeight.Bold)
                                            Text(
                                                "Pick a WAV/MP3 to test the full pipeline without live mic.",
                                                color = Muted,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick  = { if (!isUploading) filePickerLauncher.launch("audio/*") },
                                        enabled  = !isUploading,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape    = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isUploading) {
                                            CircularProgressIndicator(
                                                modifier    = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color       = Color.White
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Analysing…")
                                        } else {
                                            Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Choose Audio File")
                                        }
                                    }
                                    if (uploadStatus.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            uploadStatus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when {
                                                uploadStatus.contains("EMERGENCY") -> Rose
                                                uploadStatus.contains("Error", ignoreCase = true) -> Amber
                                                else -> Muted
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // ── Location card ──────────────────────────────────────
                            Card(
                                shape  = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Rose)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Location", fontWeight = FontWeight.Bold)
                                        Text(
                                            locationText,
                                            color = Muted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    TextButton(onClick = { getLocation() }) { Text("Refresh") }
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            // ── Manual SOS button ──────────────────────────────────
                            Button(
                                onClick = {
                                    getLocation()
                                    playBeep(context)
                                    message = "Manual SOS triggered."
                                    screen  = "sos"
                                },
                                modifier = Modifier.size(210.dp),
                                shape    = CircleShape,
                                colors   = ButtonDefaults.buttonColors(containerColor = Rose)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Sos, null, modifier = Modifier.size(54.dp))
                                    Text(
                                        "SOS",
                                        style      = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("REQUEST HELP")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Pressing SOS also plays an alert beep.",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            StatusNote(message)
                        }
                    }

                    // ── SOS screen ───────────────────────────────────────
                    "sos" -> {
                        SectionTitle("Emergency alert")
                        Icon(Icons.Default.Warning, null, tint = Rose, modifier = Modifier.size(56.dp))
                        Text(
                            "SOS request initiated",
                            style      = MaterialTheme.typography.headlineSmall,
                            color      = Rose,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Location: $locationText", color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Emergency contacts are stored in your profile. SMS integration can be added as a next step.",
                            color = Muted
                        )
                        PrimaryButton("Return home") { screen = "home" }
                        StatusNote(message)
                    }

                    // ── History ──────────────────────────────────────────
                    "history" -> {
                        SectionTitle("Safety history")
                        Text("SOS and AI detection events will appear here once event logging is implemented.", color = Muted)
                        StatusNote(message)
                    }

                    // ── Menu ─────────────────────────────────────────────
                    "menu" -> {
                        SectionTitle("Navigation")
                        listOf("home" to "Home", "history" to "Safety history").forEach { (key, label) ->
                            OutlinedButton(
                                onClick  = { screen = key },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) { Text(label) }
                        }
                        OutlinedButton(
                            onClick  = { pendingProtectedScreen = "profile"; reauthPassword = ""; screen = "reauth" },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) { Text("Edit profile") }
                        TextButton(onClick = {
                            stopWomenSOSMonitoring(context)
                            monitoring = false
                            auth?.signOut()
                            fullName = ""; password = ""
                            screen   = "welcome"
                            message  = "You have signed out."
                        }) { Text("Sign out") }
                    }

                    // ── Fallback ─────────────────────────────────────────
                    else -> {
                        SectionTitle("Women SOS")
                        Text("Unknown screen. Return to home.")
                        PrimaryButton("Home") { screen = if (auth?.currentUser != null) "home" else "welcome" }
                    }
                }
            }
        }
    }
}

// ── Helper composables ─────────────────────────────────────────────────────

@Composable
private fun PhoneField(
    label: String,
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    number: String,
    onNumberChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.width(104.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
            ) {
                Text(countryCode)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose country code")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                countryCodes.forEach { code ->
                    DropdownMenuItem(
                        text    = { Text(code) },
                        onClick = { onCountryCodeChange(code); expanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value       = number,
            onValueChange = { input -> onNumberChange(input.filter(Char::isDigit).take(10)) },
            label       = { Text(label) },
            modifier    = Modifier.weight(1f),
            singleLine  = true,
            shape       = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        singleLine    = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape         = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(52.dp),
        shape    = RoundedCornerShape(14.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StatusNote(text: String) {
    if (text.isNotBlank()) {
        Text(
            text,
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

// ── Top-level helpers ──────────────────────────────────────────────────────

private fun startWomenSOSMonitoring(context: Context, backendUrl: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) return
    val intent = Intent(context, AudioMonitoringService::class.java).apply {
        action = AudioMonitoringService.ACTION_START
        putExtra(AudioMonitoringService.EXTRA_BACKEND_URL, backendUrl)
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopWomenSOSMonitoring(context: Context) {
    val intent = Intent(context, AudioMonitoringService::class.java).apply {
        action = AudioMonitoringService.ACTION_STOP
    }
    context.startService(intent)
}

/** Play a loud alarm beep to signal an emergency detection. */
private fun playBeep(context: Context) {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 4000)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 5000)
    } catch (e: Exception) {
        Log.e("WomenSOS", "playBeep failed: ${e.message}")
    }
}