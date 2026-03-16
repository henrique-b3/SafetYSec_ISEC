package pt.isec.amov.safetysec.ui.screens.dashboard.protected

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.model.RuleType
import pt.isec.amov.safetysec.model.SafetyRule
import pt.isec.amov.safetysec.ui.viewmodels.DashboardViewModel
import java.util.Calendar
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageRulesScreen(
    navController: NavController,
    userId: String,
    monitorId: String,
    viewModel: DashboardViewModel = viewModel()
) {
    LaunchedEffect(userId) {
        viewModel.fetchRules(userId)
    }

    val rules = viewModel.currentRules.filter { it.monitorId == monitorId }

    var showDialog by remember { mutableStateOf(false) }
    var selectedRuleForEdit by remember { mutableStateOf<SafetyRule?>(null) }

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val canCreate = currentUserId == monitorId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_security_rules)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(onClick = {
                    selectedRuleForEdit = null
                    showDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_rule_title))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            if (rules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_rules_active), color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rules) { rule ->
                        RuleItem(
                            rule = rule,
                            onToggle = { isChecked ->
                                val updatedRule = rule.copy(isActive = isChecked)
                                viewModel.addRule(userId, updatedRule)
                            },
                            onEdit = {
                                selectedRuleForEdit = rule
                                showDialog = true
                            },
                            onDelete = {
                                viewModel.deleteRule(userId, rule.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        RuleDialog(
            existingRule = selectedRuleForEdit,
            onDismiss = { showDialog = false },
            onConfirm = { type, params ->
                val ruleId = selectedRuleForEdit?.id ?: ""
                val newRule = SafetyRule(
                    id = ruleId,
                    monitorId = monitorId,
                    type = type,
                    isActive = true,
                    parameters = params
                )
                viewModel.addRule(userId, newRule)
                showDialog = false
            }
        )
    }
}

@Composable
fun RuleItem(
    rule: SafetyRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val hasSchedule = rule.parameters["scheduleEnabled"] == "true"
    val scheduleText = if (hasSchedule) {
        val start = rule.parameters["startTime"] ?: "00:00"
        val end = rule.parameters["endTime"] ?: "23:59"
        " | $start - $end"
    } else ""

    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.type.name + scheduleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                when (rule.type) {
                    RuleType.SPEED -> Text("${stringResource(R.string.limit_prefix)} ${rule.parameters["value"]} km/h", style = MaterialTheme.typography.bodySmall)
                    RuleType.INACTIVITY -> Text("${stringResource(R.string.time_prefix)} ${rule.parameters["duration"]} min", style = MaterialTheme.typography.bodySmall)
                    RuleType.GEOFENCING -> Text("${stringResource(R.string.radius_prefix)} ${rule.parameters["radius"]} m", style = MaterialTheme.typography.bodySmall)
                    else -> Text(stringResource(R.string.auto_sensor_desc), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Botão de Editar
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.desc_edit_icon), tint = MaterialTheme.colorScheme.primary)
            }

            Switch(
                checked = rule.isActive,
                onCheckedChange = onToggle
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun RuleDialog(
    existingRule: SafetyRule?,
    onDismiss: () -> Unit,
    onConfirm: (RuleType, Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    // Inicializar estados com valores da regra existente (se houver) ou defaults
    var selectedType by remember { mutableStateOf(existingRule?.type ?: RuleType.FALL) }

    // Parâmetros específicos
    var paramValue by remember { mutableStateOf(existingRule?.parameters?.get("value") ?: "") }
    var paramDuration by remember { mutableStateOf(existingRule?.parameters?.get("duration") ?: "") }

    // Geofencing Params
    var paramRadius by remember { mutableStateOf(existingRule?.parameters?.get("radius") ?: "100") }
    var paramLat by remember { mutableStateOf(existingRule?.parameters?.get("latitude") ?: "") }
    var paramLon by remember { mutableStateOf(existingRule?.parameters?.get("longitude") ?: "") }

    // Janelas Temporais
    var scheduleEnabled by remember { mutableStateOf(existingRule?.parameters?.get("scheduleEnabled") == "true") }
    var startHour by remember { mutableStateOf(existingRule?.parameters?.get("startTime") ?: "09:00") }
    var endHour by remember { mutableStateOf(existingRule?.parameters?.get("endTime") ?: "18:00") }

    val toastLocObtained = stringResource(R.string.location_obtained)
    val toastLocUnavailable = stringResource(R.string.location_unavailable)
    val toastMapsError = stringResource(R.string.maps_error)

    // Dias da semana
    val initialDays = remember {
        val savedDays = existingRule?.parameters?.get("days")
        if (savedDays.isNullOrBlank()) {
            mutableStateListOf(1, 2, 3, 4, 5, 6, 7)
        } else {
            val list = savedDays.split(",").mapNotNull { it.toIntOrNull() }
            mutableStateListOf<Int>().apply { addAll(list) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingRule == null) stringResource(R.string.new_rule_title) else stringResource(R.string.edit_rule_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existingRule == null) {
                    Text(stringResource(R.string.monitoring_type), fontWeight = FontWeight.Bold)
                    RuleType.entries.forEach { type ->
                        if (type != RuleType.PANIC_BUTTON) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedType = type }
                                    .padding(4.dp)
                            ) {
                                RadioButton(selected = (selectedType == type), onClick = { selectedType = type })
                                Text(text = type.name, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider(
                        color = Color.Gray,
                        thickness = 1.dp
                    )
                } else {
                    Text(stringResource(R.string.rule_type_label, existingRule.type.name), fontWeight = FontWeight.Bold)
                    HorizontalDivider(
                        color = Color.Gray,
                        thickness = 1.dp
                    )
                }
                when (selectedType) {
                    RuleType.SPEED -> {
                        OutlinedTextField(
                            value = paramValue,
                            onValueChange = { paramValue = it },
                            label = { Text(stringResource(R.string.speed_limit_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    RuleType.INACTIVITY -> {
                        OutlinedTextField(
                            value = paramDuration,
                            onValueChange = { paramDuration = it },
                            label = { Text(stringResource(R.string.max_idle_time_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    RuleType.GEOFENCING -> {
                        OutlinedTextField(
                            value = paramRadius,
                            onValueChange = { paramRadius = it },
                            label = { Text(stringResource(R.string.radius_label) + " (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Text(stringResource(R.string.center_location), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = paramLat,
                                    onValueChange = { paramLat = it },
                                    label = { Text(stringResource(R.string.lat_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = paramLon,
                                    onValueChange = { paramLon = it },
                                    label = { Text(stringResource(R.string.lon_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }

                            Column {
                                IconButton(onClick = {
                                    // IMPLEMENTAÇÃO ATUALIZADA: USO DE FUSED LOCATION
                                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                            .addOnSuccessListener { location ->
                                                if (location != null) {
                                                    paramLat = location.latitude.toString()
                                                    paramLon = location.longitude.toString()
                                                    Toast.makeText(context, toastLocObtained, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, toastLocUnavailable, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(context, toastLocUnavailable, Toast.LENGTH_LONG).show()
                                            }
                                    } else {
                                        Toast.makeText(context, toastLocUnavailable, Toast.LENGTH_LONG).show()
                                    }
                                }) {
                                    Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.desc_location_icon), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    try {
                                        // Abre o Google Maps na posição atual ou 0,0 se vazio
                                        val lat = paramLat.ifBlank { "0" }
                                        val lon = paramLon.ifBlank { "0" }
                                        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Centro)")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        intent.setPackage("com.google.android.apps.maps")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, toastMapsError, Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Map, contentDescription = stringResource(R.string.desc_map_icon), tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                    else -> { /* Sem parametros extra */ }
                }

                HorizontalDivider(
                    color = Color.Gray,
                    thickness = 1.dp
                )

                // --- JANELAS TEMPORAIS (O Protegido controla isto) ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.time_windows), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                }

                if (scheduleEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { if (it.length <= 5) startHour = it },
                            label = { Text(stringResource(R.string.start_hour)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endHour,
                            onValueChange = { if (it.length <= 5) endHour = it },
                            label = { Text(stringResource(R.string.end_hour)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(stringResource(R.string.active_days), style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val daysLabels = listOf("D", "S", "T", "Q", "Q", "S", "S")
                        val daysValues = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)

                        daysValues.forEachIndexed { index, calendarDay ->
                            val isSelected = initialDays.contains(calendarDay)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
                                    .clickable {
                                        if (isSelected) initialDays.remove(calendarDay)
                                        else initialDays.add(calendarDay)
                                    }
                            ) {
                                Text(
                                    text = daysLabels[index],
                                    color = if (isSelected) Color.White else Color.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val params = mutableMapOf<String, String>()

                when (selectedType) {
                    RuleType.SPEED -> params["value"] = paramValue.ifBlank { "100" }
                    RuleType.INACTIVITY -> params["duration"] = paramDuration.ifBlank { "60" }
                    RuleType.GEOFENCING -> {
                        params["radius"] = paramRadius.ifBlank { "100" }
                        // Usa os valores inseridos na UI (ou via botão GPS)
                        params["latitude"] = paramLat.ifBlank { "0.0" }
                        params["longitude"] = paramLon.ifBlank { "0.0" }
                    }
                    else -> {}
                }

                params["scheduleEnabled"] = scheduleEnabled.toString()
                if (scheduleEnabled) {
                    params["startTime"] = startHour
                    params["endTime"] = endHour
                    params["days"] = initialDays.joinToString(",")
                }

                onConfirm(selectedType, params)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}