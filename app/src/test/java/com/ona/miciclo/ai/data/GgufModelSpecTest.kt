package com.ona.miciclo.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD: contrato del modelo GGUF (Qwen3-4B texto).
 * El downloader debe apuntar al .gguf oficial, exigir tamaño mínimo realista
 * y usar un directorio SEPARADO del modelo MNN para no mezclar archivos.
 * En RED falla (no existe la spec); en GREEN la verifica.
 */
class GgufModelSpecTest {

    @Test
    fun `gguf filename is the official Qwen3 Q4_K_M`() {
        assertEquals("Qwen3-4B-Q4_K_M.gguf", GgufModelSpec.FILE_NAME)
    }

    @Test
    fun `download url points to huggingface resolve`() {
        val url = GgufModelSpec.downloadUrl()
        assertTrue(url.startsWith("https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/"))
        assertTrue(url.endsWith(GgufModelSpec.FILE_NAME))
    }

    @Test
    fun `min size reflects a 4B int4 model`() {
        // Q4_K_M de 4B ≈ 2.5 GB; mínimo conservador 2 GB para no aceptar descargas truncadas.
        assertTrue(GgufModelSpec.MIN_SIZE_BYTES >= 2L * 1024 * 1024 * 1024)
    }

    @Test
    fun `required free space covers model plus margin`() {
        assertTrue(GgufModelSpec.REQUIRED_SPACE_BYTES >= GgufModelSpec.MIN_SIZE_BYTES)
    }

    @Test
    fun `model dir is separate from the legacy MNN dir`() {
        assertNotEquals("qwen_ocr_model", GgufModelSpec.MODEL_DIR_NAME)
    }
}
