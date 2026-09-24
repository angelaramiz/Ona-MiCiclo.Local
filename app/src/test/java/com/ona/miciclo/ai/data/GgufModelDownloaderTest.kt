package com.ona.miciclo.ai.data

import android.content.Context
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * TDD: contrato de [GgufModelDownloader].
 *
 * El downloader debe poder probarse en JVM sin tocar red real ni descargar
 * 2.5 GB: por eso el flujo acepta seams (url, minSizeBytes, requiredSpaceBytes,
 * usableSpace) con valores por defecto que preservan el comportamiento real.
 *
 * En RED no compila (los seams no existen); en GREEN se añaden y todo pasa.
 *
 * NOTA: no se usa `com.sun.net.httpserver` porque AGP excluye los paquetes
 * `sun.*`/`com.sun.*` de la compilación de unit tests; se sirve un mini HTTP
 * server con `ServerSocket` (solo java.base).
 */
class GgufModelDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── Mini HTTP server (solo java.base) ──────────────────────────────────────

    private class MiniHttpServer(
        private val status: Int,
        private val body: ByteArray,
        private val slowStream: Boolean = false
    ) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        @Volatile private var running = true

        val url: String get() = "http://127.0.0.1:${server.localPort}/model"

        init {
            Thread {
                while (running) {
                    try {
                        val socket = server.accept()
                        Thread {
                            try {
                                handle(socket)
                            } catch (_: IOException) {
                                // cliente desconectado
                            } finally {
                                try { socket.close() } catch (_: IOException) {}
                            }
                        }.start()
                    } catch (_: IOException) {
                        // servidor cerrado
                    }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun handle(socket: Socket) {
            val reader = socket.getInputStream().bufferedReader()
            // Leer la request line (GET /model HTTP/1.1)
            reader.readLine()
            // Descartar headers hasta línea vacía
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                line = reader.readLine()
            }
            val out = socket.getOutputStream()
            if (slowStream) {
                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                out.flush()
                while (running) {
                    out.write(ByteArray(8192))
                    out.flush()
                    Thread.sleep(20)
                }
            } else {
                out.write(statusLine(status))
                if (body.isNotEmpty()) {
                    out.write("Content-Type: application/octet-stream\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
                    out.write(body)
                } else {
                    out.write("Content-Type: application/octet-stream\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
                out.flush()
            }
        }

        private fun statusLine(status: Int): ByteArray {
            val reason = when (status) {
                200 -> "OK"
                404 -> "Not Found"
                else -> "Error"
            }
            return "HTTP/1.1 $status $reason\r\n".toByteArray()
        }

        override fun close() {
            running = false
            try { server.close() } catch (_: IOException) {}
        }
    }

    private fun downloader(filesDir: File = tmp.root): GgufModelDownloader {
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        return GgufModelDownloader(context)
    }

    private fun awaitReal(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(10)
        }
        assertTrue("Condición no alcanzada en ${timeoutMs}ms", condition())
    }

    // ── Rutas y estado ─────────────────────────────────────────────────────────

    @Test
    fun `modelDir points inside filesDir with qwen3_4b_gguf`() {
        assertEquals(File(tmp.root, "qwen3_4b_gguf"), downloader().modelDir)
    }

    @Test
    fun `modelFile is FILE_NAME inside modelDir`() {
        val d = downloader()
        assertEquals(File(d.modelDir, GgufModelSpec.FILE_NAME), d.modelFile)
    }

    @Test
    fun `isModelDownloaded returns false when model dir missing`() {
        assertFalse(downloader().isModelDownloaded())
    }

    @Test
    fun `isModelDownloaded returns false when file truncated`() {
        val d = downloader()
        d.modelDir.mkdirs()
        File(d.modelDir, GgufModelSpec.FILE_NAME).writeBytes(ByteArray(1024))
        assertFalse(d.isModelDownloaded())
    }

    @Test
    fun `isModelDownloaded returns true when file meets min size`() {
        val d = downloader()
        d.modelDir.mkdirs()
        // Sparse file de 2 GB: longitud lógica real sin ocupar disco.
        RandomAccessFile(d.modelFile, "rw").use { it.setLength(GgufModelSpec.MIN_SIZE_BYTES) }
        assertTrue(d.isModelDownloaded())
    }

    // ── deleteModel ────────────────────────────────────────────────────────────

    @Test
    fun `deleteModel returns false when model dir missing`() {
        assertFalse(downloader().deleteModel())
    }

    @Test
    fun `deleteModel deletes model dir and returns true`() {
        val d = downloader()
        d.modelDir.mkdirs()
        File(d.modelDir, "basura.tmp").writeBytes(ByteArray(10))
        assertTrue(d.deleteModel())
        assertFalse(d.modelDir.exists())
    }

    // ── downloadModel: estados del flujo ───────────────────────────────────────

    @Test
    fun `downloadModel emits error when space insufficient`() = runTest {
        downloader()
            .downloadModel(
                requiredSpaceBytes = 1_000_000L,
                usableSpace = 1_000L
            )
            .test {
                assertTrue(awaitItem() is DownloadState.Downloading)
                val err = awaitItem()
                assertTrue(err is DownloadState.Error)
                assertTrue((err as DownloadState.Error).error.message?.contains("Espacio") == true)
                awaitComplete()
            }
    }

    @Test
    fun `downloadModel skips network and succeeds when model already complete`() = runTest {
        val d = downloader()
        d.modelDir.mkdirs()
        RandomAccessFile(d.modelFile, "rw").use { it.setLength(GgufModelSpec.MIN_SIZE_BYTES) }
        d.downloadModel(
            url = "http://127.0.0.1:1/model", // puerto cerrado: si se intenta red, falla
            requiredSpaceBytes = 1L,
            usableSpace = Long.MAX_VALUE
        ).test {
            assertTrue(awaitItem() is DownloadState.Downloading)
            assertTrue(awaitItem() is DownloadState.Downloading) // 1f de "ya completo"
            assertTrue(awaitItem() is DownloadState.Success)
            awaitComplete()
        }
    }

    @Test
    fun `downloadModel with production defaults succeeds when model complete`() = runTest {
        val d = downloader()
        d.modelDir.mkdirs()
        RandomAccessFile(d.modelFile, "rw").use { it.setLength(GgufModelSpec.MIN_SIZE_BYTES) }
        // Sin seams: evalua url/requiredSpaceBytes/usableSpace por defecto (spec real).
        d.downloadModel().test {
            assertTrue(awaitItem() is DownloadState.Downloading)
            assertTrue(awaitItem() is DownloadState.Downloading) // 1f de "ya completo"
            assertTrue(awaitItem() is DownloadState.Success)
            awaitComplete()
        }
    }

    @Test
    fun `downloadModel deletes stale temp file before fresh download`() = runTest {
        MiniHttpServer(status = 200, body = ByteArray(2000)).use { server ->
            val d = downloader()
            d.modelDir.mkdirs()
            // Residuo de una descarga anterior interrumpida.
            val staleTemp = File(d.modelDir, "${GgufModelSpec.FILE_NAME}.tmp")
            staleTemp.writeBytes(ByteArray(512))

            d.downloadModel(
                url = server.url,
                minSizeBytes = 1000,
                requiredSpaceBytes = 1L,
                usableSpace = Long.MAX_VALUE
            ).test {
                assertTrue(awaitItem() is DownloadState.Downloading)
                assertTrue(awaitItem() is DownloadState.Downloading) // progreso 1f
                assertTrue(awaitItem() is DownloadState.Success)
                awaitComplete()
            }
            assertTrue(d.modelFile.exists())
            assertFalse(staleTemp.exists())
        }
    }

    @Test
    fun `downloadModel emits error when connection fails`() = runTest {
        downloader()
            .downloadModel(
                url = "http://127.0.0.1:1/model", // puerto cerrado → ConnectException
                minSizeBytes = 1000,
                requiredSpaceBytes = 1L,
                usableSpace = Long.MAX_VALUE
            )
            .test {
                assertTrue(awaitItem() is DownloadState.Downloading)
                val err = awaitItem()
                assertTrue(err is DownloadState.Error)
                awaitComplete()
            }
    }

    @Test
    fun `downloadModel emits error on HTTP 404`() = runTest {
        MiniHttpServer(status = 404, body = ByteArray(0)).use { server ->
            downloader()
                .downloadModel(
                    url = server.url,
                    minSizeBytes = 1000,
                    requiredSpaceBytes = 1L,
                    usableSpace = Long.MAX_VALUE
                )
                .test {
                    assertTrue(awaitItem() is DownloadState.Downloading)
                    val err = awaitItem()
                    assertTrue(err is DownloadState.Error)
                    assertTrue((err as DownloadState.Error).error.message?.contains("HTTP 404") == true)
                    awaitComplete()
                }
        }
    }

    @Test
    fun `downloadModel emits error when download truncated below min size`() = runTest {
        MiniHttpServer(status = 200, body = ByteArray(500)).use { server -> // < minSizeBytes=1000
            downloader()
                .downloadModel(
                    url = server.url,
                    minSizeBytes = 1000,
                    requiredSpaceBytes = 1L,
                    usableSpace = Long.MAX_VALUE
                )
                .test {
                    assertTrue(awaitItem() is DownloadState.Downloading)
                    assertTrue(awaitItem() is DownloadState.Downloading) // progreso 1f
                    val err = awaitItem()
                    assertTrue(err is DownloadState.Error)
                    assertTrue((err as DownloadState.Error).error.message?.contains("incompleta") == true)
                    awaitComplete()
                }
        }
    }

    @Test
    fun `downloadModel succeeds and renames temp to final when download complete`() = runTest {
        MiniHttpServer(status = 200, body = ByteArray(2000)).use { server -> // >= minSizeBytes=1000
            val d = downloader()
            d.downloadModel(
                url = server.url,
                minSizeBytes = 1000,
                requiredSpaceBytes = 1L,
                usableSpace = Long.MAX_VALUE
            ).test {
                assertTrue(awaitItem() is DownloadState.Downloading)
                assertTrue(awaitItem() is DownloadState.Downloading) // progreso 1f
                assertTrue(awaitItem() is DownloadState.Success)
                awaitComplete()
            }
            assertTrue(d.modelFile.exists())
            assertEquals(2000L, d.modelFile.length())
            assertFalse(File(d.modelDir, "${GgufModelSpec.FILE_NAME}.tmp").exists())
        }
    }

    @Test
    fun `downloadModel emits error when rename fails`() = runTest {
        MiniHttpServer(status = 200, body = ByteArray(2000)).use { server ->
            val d = downloader()
            d.modelDir.mkdirs()
            // targetFile es un directorio NO vacío: delete() no lo borra y renameTo falla.
            val blocker = File(d.modelDir, GgufModelSpec.FILE_NAME)
            blocker.mkdir()
            File(blocker, "contenido").writeBytes(ByteArray(10))

            d.downloadModel(
                url = server.url,
                minSizeBytes = 1000,
                requiredSpaceBytes = 1L,
                usableSpace = Long.MAX_VALUE
            ).test {
                assertTrue(awaitItem() is DownloadState.Downloading)
                assertTrue(awaitItem() is DownloadState.Downloading)
                val err = awaitItem()
                assertTrue(err is DownloadState.Error)
                assertTrue((err as DownloadState.Error).error.message?.contains("renombrar") == true)
                awaitComplete()
            }
        }
    }

    @Test
    fun `cancelling download removes partial temp file`() = runBlocking {
        MiniHttpServer(status = 200, body = ByteArray(0), slowStream = true).use { server ->
            val d = downloader()
            // El collector corre en IO real para que arranque aunque el hilo
            // principal esté bloqueado en Thread.sleep (awaitReal).
            val job = launch(Dispatchers.IO) {
                d.downloadModel(
                    url = server.url,
                    minSizeBytes = 1000,
                    requiredSpaceBytes = 1L,
                    usableSpace = Long.MAX_VALUE
                ).collect { }
            }
            // Esperar a que el flujo empiece a escribir el archivo temporal.
            val tempFile = File(d.modelDir, "${GgufModelSpec.FILE_NAME}.tmp")
            awaitReal { tempFile.exists() }

            job.cancelAndJoin()

            // La limpieza ocurre en el producer del flow (flowOn IO) tras la
            // siguiente lectura: esperar la condición de forma asíncrona.
            awaitReal { !tempFile.exists() }
            assertFalse(d.modelFile.exists())
        }
    }
}