package br.com.enchente_visual

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.enchente_visual.ui.theme.EnchentevisualTheme
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

private const val API_BASE_URL = "http://10.0.2.2:8080"
private val Ink = Color(0xFF153A36)
private val Muted = Color(0xFF55706B)
private val Teal = Color(0xFF176B62)
private val PaleGreen = Color(0xFFDCEFE8)
private val Page = Color(0xFFF5F7F4)

data class FloodInfo(
    val station: String,
    val stationCode: String,
    val stationPrefix: String,
    val localName: String,
    val river: String,
    val level: String,
    val trendStatus: String,
    val basin: String,
    val region: String,
    val altitude: String,
    val rain15s: String,
    val rain5m: String,
    val rain15m: String,
    val rain1h: String,
    val rain3h: String,
    val rain6h: String,
    val rain12h: String,
    val rain24h: String,
    val rain7d: String,
    val temperature: String,
    val humidity: String,
    val sensation: String,
    val windAverage: String,
    val windMaximum: String,
    val coordinates: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EnchentevisualTheme { FloodApp() } }
    }
}

@Composable
private fun FloodApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FloodInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fetch() {
        error = null
        loading = true
        requestLocation(context) { location ->
            if (location == null) {
                loading = false
                error = "Não foi possível obter sua localização. Verifique se o GPS está ativo."
            } else {
                fetchFloodInfo(location) { info, message ->
                    loading = false
                    result = info
                    error = message
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) fetch()
        else {
            loading = false
            error = "A localização é necessária para consultar a estação mais próxima."
        }
    }

    Scaffold(containerColor = Page) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Header() }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Consulte o rio perto de você", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text("Dados completos da estação meteorológica mais próxima da sua localização.", color = Muted, lineHeight = 22.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (allowed) fetch() else {
                                loading = true
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp) else Text("LOCALIZAÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(if (loading) "Buscando sua localização..." else "Obter informações", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (error != null) item { ErrorCard(error!!) { fetch() } }
            if (result != null) item { ResultContent(result!!) }
        }
    }
}

@Composable
private fun Header() {
    Box(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 20.dp, vertical = 26.dp)) {
        Column {
            Text("Águas do Sul", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("MONITORAMENTO EM TEMPO REAL", color = Color(0xFFB8D4CD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultContent(info: FloodInfo) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ESTAÇÃO E SITUAÇÃO", color = Color(0xFF2B8175), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Card(Modifier.weight(1.15f), colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("ESTAÇÃO MAIS PRÓXIMA", color = Color(0xFF2B8175), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(info.station, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(info.stationCode, color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Nível atual", color = Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(info.level, color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Text("m", color = Muted, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("SITUAÇÃO DO RIO", color = Color(0xFF2B8175), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(info.river, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Text(info.trendStatus, color = Teal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
        StationDetails(info)
        WeatherDetails(info)
        RainDetails(info)
        Text("Localização enviada: ${info.coordinates}", color = Color(0xFF7B918C), fontSize = 12.sp)
    }
}

@Composable
private fun StationDetails(info: FloodInfo) {
    DetailCard("DADOS DA ESTAÇÃO") {
        DetailLine("Nome geral", info.station)
        DetailLine("Nome local", info.localName)
        DetailLine("Código", info.stationCode)
        DetailLine("Prefixo", info.stationPrefix)
        DetailLine("Bacia", info.basin)
        DetailLine("Região", info.region)
        DetailLine("Altitude", info.altitude)
    }
}

@Composable
private fun RainDetails(info: FloodInfo) {
    DetailCard("CHUVA ACUMULADA") {
        Text("Volumes registrados em diferentes períodos", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("15 segundos", info.rain15s, Modifier.weight(1f)); MetricCard("5 minutos", info.rain5m, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("15 minutos", info.rain15m, Modifier.weight(1f)); MetricCard("1 hora", info.rain1h, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("3 horas", info.rain3h, Modifier.weight(1f)); MetricCard("6 horas", info.rain6h, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("12 horas", info.rain12h, Modifier.weight(1f)); MetricCard("24 horas", info.rain24h, Modifier.weight(1f)) }
            MetricCard("7 dias", info.rain7d, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WeatherDetails(info: FloodInfo) {
    DetailCard("CONDIÇÕES METEOROLÓGICAS") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Temperatura", info.temperature, Modifier.weight(1f)); MetricCard("Umidade", info.humidity, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Sensação térmica", info.sensation, Modifier.weight(1f)); MetricCard("Vento médio", info.windAverage, Modifier.weight(1f)) }
            MetricCard("Vento máximo", info.windMaximum, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF2B8175), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7F4)), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = Color(0xFF7B918C), fontSize = 11.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9E2)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = Color(0xFF8E3F2E))
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = retry) { Text("Tentar novamente") }
        }
    }
}

@SuppressLint("MissingPermission")
private fun requestLocation(context: Context, callback: (Location?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
    if (providers.isEmpty()) { callback(null); return }
    val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
    if (last != null) { callback(last); return }
    val completed = java.util.concurrent.atomic.AtomicBoolean(false)
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (completed.compareAndSet(false, true)) { manager.removeUpdates(this); callback(location) }
        }
    }
    providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
    Handler(Looper.getMainLooper()).postDelayed({ if (completed.compareAndSet(false, true)) { manager.removeUpdates(listener); callback(null) } }, 15_000L)
}

private fun fetchFloodInfo(location: Location, callback: (FloodInfo?, String?) -> Unit) {
    Executors.newSingleThreadExecutor().execute {
        try {
            val coordinates = String.format(Locale.US, "%.6f,%.6f", location.latitude, location.longitude)
            val connection = URL("$API_BASE_URL/nivel-rio?coordenadas=$coordinates").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            if (connection.responseCode !in 200..299) throw IllegalStateException("A API respondeu com erro ${connection.responseCode}.")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val rio = json.optJSONObject("rio") ?: JSONObject()
            val chuva = json.optJSONObject("chuva")?.optJSONObject("acumulado") ?: JSONObject()
            val name = json.optJSONObject("name") ?: JSONObject()
            val position = json.optJSONObject("position") ?: JSONObject()
            val info = FloodInfo(
                station = textValue(name.opt("general"), "Estação meteorológica"),
                stationCode = textValue(json.opt("codigo"), "Não informado"),
                stationPrefix = textValue(name.opt("prefix"), "Não informado"),
                localName = textValue(name.opt("local"), "Não informado"),
                river = textValue(rio.opt("rio_nome"), "Rio sem identificação"),
                level = number(rio.opt("rio_nivel"), "Não informado") + " m",
                trendStatus = rio.optString("rio_nivel_tendencia_status").ifBlank { "SEM_DADOS" }.replace("_", " "),
                basin = textValue(position.opt("bacia"), "Não informado"),
                region = textValue(position.opt("regiao"), "Não informado"),
                altitude = number(position.opt("altitude"), "Não informado") + if (position.has("altitude") && !position.isNull("altitude")) " m" else "",
                rain15s = rainValue(chuva, "s015"), rain5m = rainValue(chuva, "min005"), rain15m = rainValue(chuva, "min015"), rain1h = rainValue(chuva, "h001"), rain3h = rainValue(chuva, "h003"), rain6h = rainValue(chuva, "h006"), rain12h = rainValue(chuva, "h012"), rain24h = rainValue(chuva, "h024"), rain7d = rainValue(chuva, "h168"),
                temperature = wrappedValue(json, "temperatura", "atual", "°C"), humidity = wrappedValue(json, "umidade", "atual", "%"), sensation = wrappedValue(json, "senstermica", "atual", "°C"), windAverage = wrappedValue(json, "vento", "velocidade_media", "km/h"), windMaximum = wrappedValue(json, "vento", "velocidade_maxima", "km/h"), coordinates = coordinates
            )
            Handler(Looper.getMainLooper()).post { callback(info, null) }
        } catch (exception: Exception) {
            val message = when (exception) { is java.net.ConnectException -> "Não foi possível conectar à API. Confirme que o backend está rodando em localhost:8080."; is java.net.SocketTimeoutException -> "A API demorou para responder. Tente novamente."; else -> exception.message ?: "Não foi possível consultar a API." }
            Handler(Looper.getMainLooper()).post { callback(null, message) }
        }
    }
}

private fun rainValue(rain: JSONObject, key: String): String = wrappedValue(rain, key, "value", "mm")
private fun wrappedValue(root: JSONObject, group: String, field: String, unit: String): String = number(root.optJSONObject(group)?.optJSONObject(field)?.opt("value"), "Não informado") + " $unit"
private fun textValue(value: Any?, fallback: String): String = when (value) { is String -> value.ifBlank { fallback }; is JSONObject -> value.optString("value").ifBlank { fallback }; else -> fallback }
private fun number(value: Any?, fallback: String): String = when (value) { is Number -> String.format(Locale.getDefault(), "%.1f", value.toDouble()); is String -> value.ifBlank { fallback }; else -> fallback }
