package com.ona.miciclo.ai.data

/**
 * Contrato del modelo GGUF de texto (Qwen3-4B).
 *
 * Fuente oficial: `Qwen/Qwen3-4B-GGUF` en HuggingFace, cuantización Q4_K_M (~2.5 GB).
 * El directorio es DISTINTO al del modelo MNN legacy (`qwen_ocr_model`) para no
 * mezclar archivos y permitir borrar el anterior al migrar.
 */
object GgufModelSpec {
    const val FILE_NAME = "Qwen3-4B-Q4_K_M.gguf"
    const val MODEL_DIR_NAME = "qwen3_4b_gguf"

    private const val HF_REPO = "Qwen/Qwen3-4B-GGUF"
    private const val HF_REVISION = "main"

    /** Tamaño mínimo aceptado: Q4_K_M de 4B ≈ 2.5 GB (conservador: 2 GB). */
    const val MIN_SIZE_BYTES = 2L * 1024 * 1024 * 1024

    /** Espacio libre requerido: modelo + margen de descarga temporal. */
    const val REQUIRED_SPACE_BYTES = (3.5 * 1024 * 1024 * 1024).toLong()

    fun downloadUrl(): String =
        "https://huggingface.co/$HF_REPO/resolve/$HF_REVISION/$FILE_NAME"
}
