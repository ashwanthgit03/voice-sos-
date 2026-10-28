package com.example.womensos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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

private val Ink = Color(0xFF241B2F)
private val Rose = Color(0xFFDA315D)
private val Pale = Color(0xFFFFF4F6)
private val Muted = Color(0xFF766D7D)

private val countryCodes = listOf("+91", "+1", "+44", "+61", "+971", "+94", "+65")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WomenSosApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomenSosApp() {
    val context = LocalContext.current
    val firebaseReady = remember { FirebaseApp.initializeApp(context) != null }
    val auth = remember(firebaseReady) { if (firebaseReady) FirebaseAuth.getInstance() else null }
    val db = remember(firebaseReady) { if (firebaseReady) FirebaseFirestore.getInstance() else null }

    var screen by remember { mutableStateOf(if (auth?.currentUser == null) "welcome" else "loading") }
    var message by remember { mutableStateOf(if (firebaseReady) "" else "Firebase configuration not found. Check app/google-services.json.") }
    var busy by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+91") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var contact1Code by remember { mutableStateOf("+91") }
    var contact2Code by remember { mutableStateOf("+91") }
    var contact3Code by remember { mutableStateOf("+91") }
    var contact1 by remember { mutableStateOf("") }
    var contact2 by remember { mutableStateOf("") }
    var contact3 by remember { mutableStateOf("") }
    var emergencyEmail by remember { mutableStateOf("") }

    var address by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var locationText by remember { mutableStateOf("Location not fetched") }
    var monitoring by remember { mutableStateOf(false) }
    var prediction by remember { mutableStateOf("AI sound classifier not connected") }
    var reauthPassword by remember { mutableStateOf("") }
    var pendingProtectedScreen by remember { mutableStateOf("profile") }
    var editingExistingProfile by remember { mutableStateOf(false) }

    fun loadProfileAndRoute(userId: String) {
        if (db == null) {
            screen = "welcome"
            message = "Firebase is not initialized."
            return
        }
        busy = true
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                fullName = doc.getString("fullName").orEmpty()
                countryCode = doc.getString("countryCode") ?: "+91"
                phone = doc.getString("phoneNumber").orEmpty()
                email = doc.getString("email") ?: (auth?.currentUser?.email.orEmpty())
                contact1Code = doc.getString("contact1CountryCode") ?: "+91"
                contact2Code = doc.getString("contact2CountryCode") ?: "+91"
                contact3Code = doc.getString("contact3CountryCode") ?: "+91"
                contact1 = doc.getString("contact1").orEmpty()
                contact2 = doc.getString("contact2").orEmpty()
                contact3 = doc.getString("contact3").orEmpty()
                emergencyEmail = doc.getString("emergencyEmail").orEmpty()
                address = doc.getString("address").orEmpty()
                place = doc.getString("place").orEmpty()
                pincode = doc.getString("pincode").orEmpty()
                val complete = doc.getBoolean("setupCompleted") == true
                val step = doc.getString("setupStep") ?: "contacts"
                screen = if (complete) "home" else step
                busy = false
            }
            .addOnFailureListener {
                busy = false
                screen = "contacts"
                message = "Could not load saved setup: ${it.localizedMessage ?: "unknown error"}"
            }
    }

    LaunchedEffect(firebaseReady, auth?.currentUser?.uid) {
        val user = auth?.currentUser
        if (firebaseReady && user != null) {
            loadProfileAndRoute(user.uid)
        } else if (user == null && screen == "loading") {
            screen = "welcome"
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startWomenSOSMonitoring(context)
                monitoring = true
                message = "Sound monitoring started. This starter does not yet classify distress sounds."
            } else {
                monitoring = false
                message = "Microphone permission denied. Monitoring is off."
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
            val locationGranted =
                results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            message = "Microphone: ${if (micGranted) "allowed" else "not allowed"} • Location: ${if (locationGranted) "allowed" else "not allowed"}"
        }

    fun saveProfile(completed: Boolean, nextStep: String, onSaved: () -> Unit) {
        val user = auth?.currentUser
        if (user == null || db == null) {
            message = "Please sign in before saving profile information."
            return
        }
        val data = hashMapOf<String, Any>(
            "fullName" to fullName.trim(),
            "countryCode" to countryCode,
            "phoneNumber" to phone.filter(Char::isDigit),
            "email" to (user.email ?: email.trim()),
            "contact1CountryCode" to contact1Code,
            "contact2CountryCode" to contact2Code,
            "contact3CountryCode" to contact3Code,
            "contact1" to contact1.filter(Char::isDigit),
            "contact2" to contact2.filter(Char::isDigit),
            "contact3" to contact3.filter(Char::isDigit),
            "emergencyEmail" to emergencyEmail.trim(),
            "address" to address.trim(),
            "place" to place.trim(),
            "pincode" to pincode.filter(Char::isDigit),
            "setupCompleted" to completed,
            "setupStep" to nextStep,
            "updatedAt" to System.currentTimeMillis()
        )
        busy = true
        db.collection("users").document(user.uid).set(data)
            .addOnSuccessListener {
                busy = false
                message = "Information saved."
                onSaved()
            }
            .addOnFailureListener {
                busy = false
                message = "Could not save information: ${it.localizedMessage ?: "unknown error"}"
            }
    }

    fun getLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    locationText = if (loc != null) "${loc.latitude}, ${loc.longitude}"
                    else "No recent location. Enable device location and try again."
                }
                .addOnFailureListener { locationText = "Location unavailable: ${it.localizedMessage}" }
        } catch (_: SecurityException) {
            locationText = "Location permission required."
        }
    }

    fun validIndianOrSelectedPhone(number: String): Boolean = number.length == 10 && number.all(Char::isDigit)

    MaterialTheme(colorScheme = lightColorScheme(primary = Rose, onPrimary = Color.White, background = Pale, surface = Color.White)) {
        val authenticated = auth?.currentUser != null
        val authScreens = screen in listOf("welcome", "login", "register", "loading")
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("WOMEN SOS", fontWeight = FontWeight.ExtraBold, color = Ink) },
                    navigationIcon = {
                        if (authenticated && !authScreens && screen != "setupContacts" && screen != "setupAddress" && screen != "reauth") {
                            IconButton(onClick = { screen = if (screen == "menu") "home" else "menu" }) {
                                Icon(Icons.Default.Menu, contentDescription = "Navigation")
                            }
                        }
                    },
                    actions = { Icon(Icons.Default.Shield, contentDescription = "Safety", tint = Rose, modifier = Modifier.padding(end = 16.dp)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Pale)
                )
            },
            bottomBar = {
                if (authenticated && screen in listOf("home", "history", "profile")) {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                        NavigationBarItem(selected = screen == "history", onClick = { screen = "history" }, icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
                        NavigationBarItem(selected = screen == "profile", onClick = {
                            pendingProtectedScreen = "profile"
                            editingExistingProfile = true
                            reauthPassword = ""
                            screen = "reauth"
                        }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
                    }
                }
            },
            containerColor = Pale
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (screen) {
                    "loading" -> {
                        CircularProgressIndicator(color = Rose)
                        Text("Loading your account…", color = Muted)
                    }

                    "welcome" -> {
                        Spacer(Modifier.height(32.dp))
                        Icon(Icons.Default.Shield, null, tint = Rose, modifier = Modifier.size(76.dp))
                        Text("Your safety matters.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
                        Text("A personal safety companion with SOS alerts and optional audio monitoring.", color = Muted)
                        Spacer(Modifier.height(28.dp))
                        PrimaryButton("Create account") { message = ""; screen = "register" }
                        OutlinedButton(onClick = { message = ""; screen = "login" }, modifier = Modifier.fillMaxWidth()) { Text("I already have an account") }
                        StatusNote(message)
                    }

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
                                phone.length != 10 || !phone.all(Char::isDigit) -> message = "Enter exactly 10 digits for your phone number."
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
                                                message = "Account created, but user ID was unavailable. Please sign in."
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
                                                        busy = false
                                                        password = ""
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
                                        .addOnFailureListener {
                                            busy = false
                                            message = it.localizedMessage ?: "Registration failed."
                                        }
                                }
                            }
                        }
                        TextButton(onClick = { message = ""; screen = "login" }) { Text("Already have an account? Sign in") }
                        StatusNote(message)
                    }

                    "login" -> {
                        SectionTitle("Welcome back")
                        Text("Sign in to open your safety home screen.", color = Muted)
                        Field("Email address", email, { email = it })
                        Field("Password", password, { password = it }, password = true)
                        PrimaryButton(if (busy) "Signing in…" else "Sign in") {
                            if (busy) return@PrimaryButton
                            if (email.isBlank() || password.isBlank()) {
                                message = "Enter your email and password."
                            } else if (auth == null) {
                                message = "Firebase is not configured."
                            } else {
                                busy = true
                                auth.signInWithEmailAndPassword(email.trim(), password)
                                    .addOnSuccessListener { result ->
                                        password = ""
                                        busy = false
                                        result.user?.uid?.let { loadProfileAndRoute(it) }
                                            ?: run { screen = "welcome"; message = "Could not read account." }
                                    }
                                    .addOnFailureListener {
                                        busy = false
                                        message = it.localizedMessage ?: "Login failed."
                                    }
                            }
                        }
                        TextButton(onClick = { message = ""; screen = "register" }) { Text("New here? Create an account") }
                        StatusNote(message)
                    }

                    "setupContacts" -> {
                        SectionTitle("Emergency contacts • 2 of 3")
                        Text("Add three trusted people and an emergency email address.", color = Muted)
                        PhoneField("Trusted contact 1", contact1Code, { contact1Code = it }, contact1, { contact1 = it })
                        PhoneField("Trusted contact 2", contact2Code, { contact2Code = it }, contact2, { contact2 = it })
                        PhoneField("Trusted contact 3", contact3Code, { contact3Code = it }, contact3, { contact3 = it })
                        Field("Emergency email address", emergencyEmail, { emergencyEmail = it })
                        PrimaryButton(if (busy) "Saving…" else "Next") {
                            if (busy) return@PrimaryButton
                            if (!validIndianOrSelectedPhone(contact1) || !validIndianOrSelectedPhone(contact2) || !validIndianOrSelectedPhone(contact3)) {
                                message = "Enter exactly 10 digits for all three contact phone numbers."
                            } else if (emergencyEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emergencyEmail.trim()).matches()) {
                                message = "Enter a valid emergency email address or leave it blank."
                            } else {
                                saveProfile(completed = false, nextStep = "setupAddress") { screen = "setupAddress" }
                            }
                        }
                        TextButton(onClick = { screen = "register" }) { Text("Back") }
                        StatusNote(message)
                    }

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
                                place.isBlank() -> message = "Enter your place or city."
                                pincode.length != 6 || !pincode.all(Char::isDigit) -> message = "Enter a 6-digit PIN code."
                                else -> saveProfile(completed = true, nextStep = "home") {
                                    message = "Setup complete. Stay safe."
                                    screen = "home"
                                }
                            }
                        }
                        TextButton(onClick = { screen = "setupContacts" }) { Text("Back") }
                        StatusNote(message)
                    }

                    "reauth" -> {
                        SectionTitle("Verify it's you")
                        Text("Enter your current account password before changing your saved details.", color = Muted)
                        Field("Current password", reauthPassword, { reauthPassword = it }, password = true)
                        PrimaryButton(if (busy) "Verifying…" else "Continue") {
                            val user = auth?.currentUser
                            if (user?.email.isNullOrBlank() || reauthPassword.isBlank()) {
                                message = "Enter your current password."
                            } else {
                                busy = true
                                val credential = EmailAuthProvider.getCredential(user!!.email!!, reauthPassword)
                                user.reauthenticate(credential)
                                    .addOnSuccessListener {
                                        busy = false
                                        reauthPassword = ""
                                        screen = pendingProtectedScreen
                                    }
                                    .addOnFailureListener {
                                        busy = false
                                        message = "Password verification failed: ${it.localizedMessage ?: "try again"}"
                                    }
                            }
                        }
                        TextButton(onClick = { screen = "home" }) { Text("Cancel") }
                        StatusNote(message)
                    }

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
                            if (fullName.isBlank()) message = "Enter your name."
                            else if (phone.length != 10 || !phone.all(Char::isDigit)) message = "Enter exactly 10 digits for your phone number."
                            else if (pincode.isNotBlank() && (pincode.length != 6 || !pincode.all(Char::isDigit))) message = "PIN code must be 6 digits."
                            else saveProfile(completed = true, nextStep = "home") { screen = "home" }
                        }
                        OutlinedButton(onClick = {
                            pendingProtectedScreen = "contacts"
                            reauthPassword = ""
                            screen = "reauth"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Edit emergency contacts") }
                        StatusNote(message)
                    }

                    "contacts" -> {
                        SectionTitle("Edit emergency contacts")
                        PhoneField("Trusted contact 1", contact1Code, { contact1Code = it }, contact1, { contact1 = it })
                        PhoneField("Trusted contact 2", contact2Code, { contact2Code = it }, contact2, { contact2 = it })
                        PhoneField("Trusted contact 3", contact3Code, { contact3Code = it }, contact3, { contact3 = it })
                        Field("Emergency email address", emergencyEmail, { emergencyEmail = it })
                        PrimaryButton(if (busy) "Saving…" else "Save contacts") {
                            if (!validIndianOrSelectedPhone(contact1) || !validIndianOrSelectedPhone(contact2) || !validIndianOrSelectedPhone(contact3)) {
                                message = "Enter exactly 10 digits for all three contact numbers."
                            } else if (emergencyEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emergencyEmail.trim()).matches()) {
                                message = "Enter a valid emergency email address."
                            } else saveProfile(completed = true, nextStep = "home") { screen = "profile" }
                        }
                        TextButton(onClick = { screen = "profile" }) { Text("Back to profile") }
                        StatusNote(message)
                    }

                    "home" -> {
                        if (auth?.currentUser == null) {
                            LaunchedEffect(Unit) { screen = "welcome" }
                        } else {
                            Text("Hello${if (fullName.isNotBlank()) ", $fullName" else ""}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                            Text("You deserve to feel safe.", color = Muted)
                            Spacer(Modifier.height(18.dp))
                            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Mic, null, tint = Rose)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Audio monitoring", fontWeight = FontWeight.Bold)
                                            Text(if (monitoring) "Active foreground service" else "Off", color = Muted)
                                        }
                                        Switch(
                                            checked = monitoring,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        startWomenSOSMonitoring(context)
                                                        monitoring = true
                                                        message = "Sound monitoring started; sound classification is not connected yet."
                                                    } else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                } else {
                                                    stopWomenSOSMonitoring(context)
                                                    monitoring = false
                                                    message = "Sound monitoring stopped."
                                                }
                                            }
                                        )
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Sensors, null, tint = Rose)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("AI model status", fontWeight = FontWeight.Bold)
                                            Text(prediction, color = Muted)
                                        }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, null, tint = Rose)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Location", fontWeight = FontWeight.Bold)
                                            Text(locationText, color = Muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { getLocation() }) { Text("Refresh") }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    getLocation()
                                    message = "SOS requested. Actual SMS/email alerts require a configured trusted backend."
                                    screen = "sos"
                                },
                                modifier = Modifier.size(210.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Rose)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Sos, null, modifier = Modifier.size(54.dp))
                                    Text("SOS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                                    Text("REQUEST HELP")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Manual SOS works independently of AI detection.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            PrimaryButton("Edit profile") {
                                pendingProtectedScreen = "profile"
                                reauthPassword = ""
                                screen = "reauth"
                            }
                            StatusNote(message)
                        }
                    }

                    "sos" -> {
                        SectionTitle("Emergency alert")
                        Icon(Icons.Default.Warning, null, tint = Rose, modifier = Modifier.size(56.dp))
                        Text("SOS request initiated", style = MaterialTheme.typography.headlineSmall, color = Rose, fontWeight = FontWeight.Bold)
                        Text("Location: $locationText", color = Muted)
                        Text("This build does not yet send SMS or email alerts. Do not rely on it as your only emergency safety method.", color = Muted)
                        PrimaryButton("Return home") { screen = "home" }
                        StatusNote(message)
                    }

                    "history" -> {
                        SectionTitle("Safety history")
                        Text("SOS and audio event history will appear here after event logging is implemented.", color = Muted)
                        StatusNote(message)
                    }

                    "menu" -> {
                        SectionTitle("Navigation")
                        listOf("home" to "Home", "history" to "Safety history").forEach { (key, label) ->
                            OutlinedButton(onClick = { screen = key }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label) }
                        }
                        OutlinedButton(onClick = {
                            pendingProtectedScreen = "profile"
                            reauthPassword = ""
                            screen = "reauth"
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("Edit profile") }
                        TextButton(onClick = {
                            stopWomenSOSMonitoring(context)
                            monitoring = false
                            auth?.signOut()
                            fullName = ""
                            password = ""
                            screen = "welcome"
                            message = "You have signed out."
                        }) { Text("Sign out") }
                    }

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
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(104.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)) {
                Text(countryCode)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose country code")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                countryCodes.forEach { code ->
                    DropdownMenuItem(text = { Text(code) }, onClick = {
                        onCountryCodeChange(code)
                        expanded = false
                    })
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = number,
            onValueChange = { input -> onNumberChange(input.filter(Char::isDigit).take(10)) },
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
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
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusNote(text: String) {
    if (text.isNotBlank()) {
        Text(text, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

private fun startWomenSOSMonitoring(context: android.content.Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
    val intent = Intent(context, AudioMonitoringService::class.java).apply {
        action = AudioMonitoringService.ACTION_START
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopWomenSOSMonitoring(context: android.content.Context) {
    val intent = Intent(context, AudioMonitoringService::class.java).apply {
        action = AudioMonitoringService.ACTION_STOP
    }
    context.startService(intent)
}
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}