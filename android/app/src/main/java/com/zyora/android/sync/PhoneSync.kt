package com.zyora.android.sync

import android.content.Context
import android.os.Build
import com.zyora.service.Planner
import com.zyora.service.PlannerJson
import com.zyora.service.SyncMerger
import com.zyora.service.SyncProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant

/** Where the last sync stands, for the screen that shows it. */
sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val at: Instant) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Syncs with Zyora on the computer over Wi‑Fi (see [SyncProtocol]): sends everything, gets back
 * the merge of both sides and merges that once more with whatever changed here meanwhile.
 * The planner is only touched on the main thread; the network part runs on IO.
 */
class PhoneSync(context: Context, private val planner: Planner, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<SyncState>(
        prefs.getLong(LAST, 0).takeIf { it > 0 }?.let { SyncState.Done(Instant.ofEpochMilli(it)) } ?: SyncState.Idle
    )
    val state: StateFlow<SyncState> = _state

    /** Something changed here since the last sync. */
    private var dirty = true

    init {
        planner.addListener { dirty = true }
    }

    val host: String get() = prefs.getString(HOST, "") ?: ""
    val code: String get() = prefs.getString(CODE, "") ?: ""
    val isConfigured: Boolean get() = host.isNotBlank() && code.isNotBlank()

    fun configure(host: String, code: String) {
        prefs.edit().putString(HOST, host.trim()).putString(CODE, code.filter { it.isDigit() }).apply()
    }

    /** Syncs now, unless a sync is already running. */
    fun syncNow() {
        if (!isConfigured || _state.value == SyncState.Running) return
        scope.launch { run() }
    }

    /** Called when the app is closed: sends what changed, if anything did. */
    fun syncIfChanged() {
        if (dirty) syncNow()
    }

    private suspend fun run() {
        _state.value = SyncState.Running
        val body = PlannerJson.write(planner.snapshot())
        val host = host
        val code = code
        val answer = withContext(Dispatchers.IO) { runCatching { post(host, code, body) } }
        answer.fold(
            onSuccess = { bytes ->
                val fromPc = PlannerJson.read(bytes.inputStream())
                planner.replaceWith(SyncMerger.merge(fromPc, planner.snapshot(), Instant.now()))
                dirty = false
                val now = Instant.now()
                prefs.edit().putLong(LAST, now.toEpochMilli()).apply()
                _state.value = SyncState.Done(now)
            },
            onFailure = { _state.value = SyncState.Failed(explain(it, host)) },
        )
    }

    private fun post(host: String, code: String, body: ByteArray): ByteArray {
        val address = if (':' in host) host else "$host:${SyncProtocol.PORT}"
        val connection = URL("http://$address${SyncProtocol.PATH}").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty(SyncProtocol.CODE_HEADER, code)
            connection.setRequestProperty(SyncProtocol.DEVICE_HEADER, Build.MODEL ?: "Teléfono")
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status != 200) {
                val text = connection.errorStream?.use { String(it.readBytes()) }.orEmpty()
                throw PcRefused(text.ifBlank { "El PC respondió con un error ($status)." })
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private class PcRefused(message: String) : IOException(message)

    private fun explain(error: Throwable, host: String): String = when (error) {
        is PcRefused -> error.message.orEmpty()
        is ConnectException, is SocketTimeoutException, is NoRouteToHostException, is UnknownHostException ->
            "No se encontró el PC en $host. Revisa que Zyora esté abierta en el PC y que los dos estén en la misma Wi‑Fi."
        else -> "No se pudo sincronizar: ${error.message ?: error.javaClass.simpleName}"
    }

    private companion object {
        const val HOST = "host"
        const val CODE = "code"
        const val LAST = "lastSync"
    }
}
