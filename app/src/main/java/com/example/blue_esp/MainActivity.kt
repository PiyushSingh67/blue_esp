package com.example.blue_esp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.blue_esp.bluetooth.BluetoothService
import com.example.blue_esp.database.User
import com.example.blue_esp.server.EspDataRepository
import com.example.blue_esp.server.EspState
import com.example.blue_esp.server.startKtorServer
import com.example.blue_esp.ui.theme.Blue_espTheme
import com.example.blue_esp.viewmodel.BluetoothViewModel
import com.example.blue_esp.viewmodel.ConnectionState
import com.example.blue_esp.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: BluetoothViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()

    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.all { it.value }
        viewModel.onPermissionsResult(allGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceIntent = Intent(this, BluetoothService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Thread { startKtorServer() }.start()

        setContent {
            Blue_espTheme {
                val navController = rememberNavController()
                val connectionState by viewModel.connectionState.collectAsState()
                val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                val espState by EspDataRepository.state.collectAsState()
                val users by userViewModel.allUsers.collectAsState()

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            navController, 
                            espState, 
                            connectionState, 
                            users.firstOrNull(), 
                            onScanClick = { checkPermissions() }
                        )
                    }
                    composable("profile") {
                        ProfileEditScreen(navController, userViewModel, users.firstOrNull())
                    }
                    composable("devices") {
                        DeviceListScreen(navController, discoveredDevices, viewModel)
                    }
                }
            }
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestMultiplePermissions.launch(permissions.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    espState: EspState,
    connectionState: ConnectionState,
    user: User?,
    onScanClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Node Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.size(32.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                onScanClick()
                navController.navigate("devices") 
            }) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = "Scan")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            
            // User Brief Profile
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val painter = if (user?.profilePictureUri != null) {
                        rememberAsyncImagePainter(user.profilePictureUri)
                    } else {
                        rememberAsyncImagePainter(Icons.Default.Person)
                    }
                    
                    Image(
                        painter = painter,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(user?.username ?: "Anonymous User", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Blood Group: ${user?.bloodGroup ?: "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Real-time Sensor Data
            Text("Live Telemetry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = if(connectionState == ConnectionState.Connected) Color.Green else Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(espState.connectionStatus, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("ESP32 Output:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Text(espState.lastReceivedData, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Updated: ${if(espState.timestamp == 0L) "N/A" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(espState.timestamp))}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Server Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Ktor Bridge Running", fontWeight = FontWeight.Bold)
                        Text("LAN IP Port: 8080", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(navController: NavHostController, viewModel: UserViewModel, user: User?) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var age by remember { mutableStateOf(user?.age?.toString() ?: "") }
    var gender by remember { mutableStateOf(user?.gender ?: "") }
    var bloodGroup by remember { mutableStateOf(user?.bloodGroup ?: "") }
    var imageUri by remember { mutableStateOf(user?.profilePictureUri) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri?.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).clickable { launcher.launch("image/*") }) {
                val painter = if (imageUri != null) rememberAsyncImagePainter(imageUri) else rememberAsyncImagePainter(Icons.Default.Person)
                Image(
                    painter = painter,
                    contentDescription = "Profile",
                    modifier = Modifier.size(120.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp), tint = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email/Credentials") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bloodGroup, onValueChange = { bloodGroup = it }, label = { Text("Blood Group") }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    viewModel.saveUser(User(
                        id = user?.id ?: 0,
                        username = username,
                        email = email,
                        age = age.toIntOrNull() ?: 0,
                        gender = gender,
                        bloodGroup = bloodGroup,
                        profilePictureUri = imageUri
                    ))
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                Text("Save Changes")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(navController: NavHostController, devices: List<BluetoothDevice>, viewModel: BluetoothViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby ESP32s") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(devices) { device ->
                ListItem(
                    headlineContent = { Text(device.name ?: "Unnamed Device") },
                    supportingContent = { Text(device.address) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        viewModel.connectToDevice(device)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
