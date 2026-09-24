package com.ona.miciclo.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen3PromptTest {

    @Test
    fun `format wraps system and user in chatml with assistant prime`() {
        val out = Qwen3Prompt.format("Eres Ona.", "¿Cuándo ovulo?")
        assertTrue(out.startsWith("<|im_start|>system\nEres Ona.<|im_end|>\n"))
        assertTrue(out.contains("<|im_start|>user\n"))
        assertTrue(out.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `thinking disabled by default with no_think suffix`() {
        val out = Qwen3Prompt.format("sys", "hola")
        assertTrue(out.contains("hola /no_think<|im_end|>"))
        assertFalse(out.contains("<think>"))
    }

    @Test
    fun `thinking can be enabled explicitly`() {
        val out = Qwen3Prompt.format("sys", "hola", enableThinking = true)
        assertFalse(out.contains("/no_think"))
        assertTrue(out.contains("hola<|im_end|>"))
    }

    @Test
    fun `stripThinking removes think blocks`() {
        val raw = "<think>razono...</think>{\"a\":1}"
        assertEquals("{\"a\":1}", Qwen3Prompt.stripThinking(raw))
    }

    @Test
    fun `stripThinking keeps clean text untouched`() {
        val raw = "{\"proxima_menstruacion_fecha\":\"2026-10-19\"}"
        assertEquals(raw, Qwen3Prompt.stripThinking(raw))
    }
}
