package com.ona.miciclo.settings.presentation

import org.junit.Assert.*
import org.junit.Test

class DeleteConfirmLabelTest {

    @Test
    fun `muestra cuenta regresiva mientras hay segundos`() {
        assertEquals("Eliminar todo (5)", deleteConfirmLabel(5))
        assertEquals("Eliminar todo (3)", deleteConfirmLabel(3))
        assertEquals("Eliminar todo (1)", deleteConfirmLabel(1))
    }

    @Test
    fun `sin contador al llegar a cero`() {
        assertEquals("Eliminar todo", deleteConfirmLabel(0))
        assertEquals("Eliminar todo", deleteConfirmLabel(-1))
    }

    @Test
    fun `constante de espera`() {
        assertEquals(5, DELETE_CONFIRM_SECONDS)
    }
}
