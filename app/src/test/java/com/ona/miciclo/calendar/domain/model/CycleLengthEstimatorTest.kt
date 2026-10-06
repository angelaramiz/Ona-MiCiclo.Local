package com.ona.miciclo.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * TDD: el largo del ciclo debe recalibrarse con cada ciclo registrado
 * (promedio móvil de hasta los últimos 6 ciclos válidos), no quedarse fijo.
 */
class CycleLengthEstimatorTest {

    private fun starts(vararg isoDates: String, newestFirst: Boolean = true): List<LocalDate> {
        val dates = isoDates.map { LocalDate.parse(it) }
        return if (newestFirst) dates else dates.reversed()
    }

    @Test
    fun `menos de 3 ciclos usa default con confianza baja`() {
        val r = CycleLengthEstimator.estimate(
            starts("2026-10-01", "2026-09-03"),
            defaultLength = 28
        )
        assertEquals(28, r.length)
        assertEquals(PredictionConfidence.LOW, r.confidence)
    }

    @Test
    fun `3 ciclos estables dan promedio con confianza alta`() {
        // 28, 28, 28 días entre inicios
        val r = CycleLengthEstimator.estimate(
            starts("2026-10-25", "2026-09-27", "2026-08-30", "2026-08-02"),
            defaultLength = 28
        )
        assertEquals(28, r.length)
        assertEquals(PredictionConfidence.HIGH, r.confidence)
    }

    @Test
    fun `el promedio se ajusta cuando los ciclos se acortan`() {
        // 30 -> 28 -> 26: promedio 28 (antes quedaba anclado con solo 2 muestras)
        val r = CycleLengthEstimator.estimate(
            starts("2026-10-23", "2026-09-27", "2026-08-30", "2026-07-31"),
            defaultLength = 28
        )
        assertEquals(28, r.length)
        assertEquals(PredictionConfidence.HIGH, r.confidence)
    }

    @Test
    fun `alta variabilidad marca confianza media`() {
        // 21, 28, 35: σ ≈ 5.7 > 5
        val r = CycleLengthEstimator.estimate(
            starts("2026-10-16", "2026-09-25", "2026-08-28", "2026-07-24"),
            defaultLength = 28
        )
        assertEquals(28, r.length)
        assertEquals(PredictionConfidence.MEDIUM, r.confidence)
    }

    @Test
    fun `duraciones implausibles se descartan`() {
        // Hueco de 90 días (olvido de registro) no debe contaminar el promedio
        val r = CycleLengthEstimator.estimate(
            starts("2026-10-25", "2026-09-27", "2026-08-30", "2026-05-31"),
            defaultLength = 28
        )
        assertEquals(28, r.length)
        assertEquals(PredictionConfidence.LOW, r.confidence)
    }

    @Test
    fun `usa hasta 6 ciclos para mayor precision`() {
        // 6 muestras de 26 días
        val r = CycleLengthEstimator.estimate(
            starts(
                "2026-10-20", "2026-09-24", "2026-08-29",
                "2026-08-03", "2026-07-08", "2026-06-12", "2026-05-17"
            ),
            defaultLength = 28
        )
        assertEquals(26, r.length)
        assertEquals(PredictionConfidence.HIGH, r.confidence)
    }
}
