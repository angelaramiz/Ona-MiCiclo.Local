package com.ona.miciclo.ai.data

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlamaCppInferenceEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `initially not loaded`() {
        assertFalse(LlamaCppInferenceEngine().isModelLoaded())
    }

    @Test
    fun `loadModel with missing file fails`() = runTest {
        val result = LlamaCppInferenceEngine().loadModel("/no/existe/modelo.gguf")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no encontrado") == true)
    }

    @Test
    fun `loadModel with truncated file fails`() = runTest {
        val tiny = tmp.newFile("tiny.gguf")
        tiny.writeBytes(ByteArray(1024))
        val result = LlamaCppInferenceEngine().loadModel(tiny.absolutePath)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("incompleto") == true)
    }

    @Test
    fun `generateResponse without load throws`() = runTest {
        val engine = LlamaCppInferenceEngine()
        var thrown: Throwable? = null
        engine.generateResponse("hola").catch { thrown = it }.collect { }
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `unloadModel keeps engine unloaded`() = runTest {
        val engine = LlamaCppInferenceEngine()
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
