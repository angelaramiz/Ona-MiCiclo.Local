package com.ona.miciclo.ai.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlamaCppInferenceEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun engineWithModelFile(file: File): LlamaCppInferenceEngine {
        val downloader = mockk<GgufModelDownloader>()
        every { downloader.modelFile } returns file
        return LlamaCppInferenceEngine(downloader)
    }

    @Test
    fun `initially not loaded`() {
        assertFalse(engineWithModelFile(File("/no/existe/modelo.gguf")).isModelLoaded())
    }

    @Test
    fun `loadModel with missing file fails`() = runTest {
        val result = engineWithModelFile(File("/no/existe/modelo.gguf"))
            .loadModel("/no/existe/modelo.gguf")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no encontrado") == true)
    }

    @Test
    fun `loadModel with truncated file fails`() = runTest {
        val tiny = tmp.newFile("tiny.gguf")
        tiny.writeBytes(ByteArray(1024))
        val result = engineWithModelFile(tiny).loadModel(tiny.absolutePath)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("incompleto") == true)
    }

    @Test
    fun `generateResponse lazy-loads and throws when model file is missing`() = runTest {
        val engine = engineWithModelFile(File("/no/existe/modelo.gguf"))
        var thrown: Throwable? = null
        engine.generateResponse("hola").catch { thrown = it }.collect { }
        assertNotNull(thrown)
        assertTrue(thrown!!.message?.contains("GGUF", ignoreCase = true) == true)
    }

    @Test
    fun `unloadModel keeps engine unloaded`() = runTest {
        val engine = engineWithModelFile(File("/no/existe/modelo.gguf"))
        engine.unloadModel()
        assertFalse(engine.isModelLoaded())
    }

    @Test
    fun `ensureLoaded degrades gracefully without native lib`() {
        // En JVM no existe libona_llama.so: debe devolver false sin lanzar
        // (UnsatisfiedLinkError es Throwable y se captura dentro).
        assertFalse(LlamaCppBridge.ensureLoaded())
    }
}