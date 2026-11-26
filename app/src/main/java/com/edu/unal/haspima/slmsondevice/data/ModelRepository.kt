package com.edu.unal.haspima.slmsondevice.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Progreso de una descarga o importación. total = -1 si se desconoce. */
data class TransferProgress(val bytes: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Archivos de modelo en el dispositivo: descarga (reanudable), importación vía SAF y borrado.
 *
 * Ruta: getExternalFilesDir("models") =
 *   /sdcard/Android/data/com.edu.unal.haspima.slmsondevice/files/models/
 * (permite copiar modelos con `adb push`). Si no hay almacenamiento externo, usa filesDir/models.
 */
class ModelRepository(private val context: Context) {

    val modelsDir: File by lazy {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.apply { mkdirs() }
    }

    fun fileFor(model: ModelInfo) = File(modelsDir, model.fileName)

    /** Modelos .litertlm completos presentes en disco (descargados, importados o vía adb). */
    fun localModels(): List<File> =
        modelsDir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }
            ?.sortedBy { it.name } ?: emptyList()

    fun partialBytes(model: ModelInfo) = partFile(model).takeIf { it.exists() }?.length() ?: 0L

    fun delete(file: File): Boolean {
        File(file.path + PART).delete()
        return file.delete()
    }

    /**
     * Descarga con HttpURLConnection, reanudable con la cabecera Range sobre un archivo .part.
     * Las redirecciones se siguen a mano para enviar el token solo a huggingface.co.
     */
    fun download(model: ModelInfo, hfToken: String?): Flow<TransferProgress> = flow {
        val part = partFile(model)
        var already = if (part.exists()) part.length() else 0L
        if (model.sizeBytes in 1..already) {
            // El .part ya está completo (descarga interrumpida justo al final).
            finish(model, part)
            return@flow
        }
        val conn = openFollowingRedirects(model.url, hfToken, already)
        try {
            val code = conn.responseCode
            when {
                code == 401 || code == 403 -> throw IOException(
                    "Acceso denegado ($code). Modelo restringido: acepte la licencia en Hugging Face y configure un token."
                )
                code == 416 -> { part.delete(); throw IOException("Rango inválido; reintente la descarga.") }
                code !in 200..299 -> throw IOException("HTTP $code al descargar ${model.fileName}")
            }
            // 206 = el servidor aceptó reanudar; 200 = empieza desde cero.
            val append = code == HttpURLConnection.HTTP_PARTIAL && already > 0
            if (!append) already = 0
            val total = conn.contentLengthLong.let { if (it > 0) it + already else model.sizeBytes }
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    copyWithProgress(input, out, already, total) { emit(it) }
                }
            }
        } finally {
            conn.disconnect()
        }
        finish(model, part)
    }.flowOn(Dispatchers.IO)

    private fun finish(model: ModelInfo, part: File) {
        if (model.sizeBytes > 0 && part.length() != model.sizeBytes) {
            if (part.length() > model.sizeBytes) part.delete()
            throw IOException("Tamaño inesperado (${part.length()} de ${model.sizeBytes} bytes); reintente.")
        }
        checkMagic(part)
        if (!part.renameTo(fileFor(model))) throw IOException("No se pudo mover el archivo descargado")
    }

    /** Copia un .litertlm elegido con el Storage Access Framework a la carpeta de modelos. */
    fun import(uri: Uri): Flow<TransferProgress> = flow {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val target = File(modelsDir, sanitizeModelFileName(name))
        val tmp = File(target.path + PART)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
            input.use { i -> FileOutputStream(tmp).use { o -> copyWithProgress(i, o, 0, size) { emit(it) } } }
            checkMagic(tmp)
            if (!tmp.renameTo(target)) throw IOException("No se pudo guardar ${target.name}")
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------------------------------

    private fun partFile(model: ModelInfo) = File(modelsDir, model.fileName + PART)

    private fun openFollowingRedirects(start: String, token: String?, rangeFrom: Long): HttpURLConnection {
        var url = URL(start)
        repeat(MAX_REDIRECTS) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                if (rangeFrom > 0) setRequestProperty("Range", "bytes=$rangeFrom-")
                if (!token.isNullOrBlank() && url.host.endsWith("huggingface.co")) {
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                conn.disconnect()
                url = URL(url, location)
            } else {
                return conn
            }
        }
        throw IOException("Demasiadas redirecciones")
    }

    private suspend inline fun copyWithProgress(
        input: InputStream,
        out: FileOutputStream,
        startAt: Long,
        total: Long,
        onProgress: (TransferProgress) -> Unit,
    ) {
        val buffer = ByteArray(256 * 1024)
        var copied = startAt
        var lastReport = 0L
        while (true) {
            currentCoroutineContext().ensureActive() // permite cancelar la descarga
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            copied += n
            if (copied - lastReport >= REPORT_EVERY) {
                lastReport = copied
                onProgress(TransferProgress(copied, total))
            }
        }
        onProgress(TransferProgress(copied, total))
    }

    /** Validación mínima: los .litertlm empiezan con la cadena mágica "LITERTLM". */
    private fun checkMagic(file: File) {
        val header = ByteArray(8)
        val read = file.inputStream().use { it.read(header) }
        if (read != 8 || !hasLiteRtLmMagic(header)) {
            file.delete()
            throw IOException("El archivo no es un modelo .litertlm válido")
        }
    }

    companion object {
        private const val PART = ".part"
        private const val MAX_REDIRECTS = 8
        private const val REPORT_EVERY = 2L * 1024 * 1024
    }
}

fun hasLiteRtLmMagic(header: ByteArray): Boolean =
    header.size >= 8 && String(header, 0, 8, Charsets.US_ASCII) == "LITERTLM"
