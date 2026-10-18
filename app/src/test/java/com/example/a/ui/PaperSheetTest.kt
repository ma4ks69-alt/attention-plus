package com.example.a.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperSheetTest {
    private val hinge = 130f
    private val top = 13f
    private val depth = 26f
    private val length = 330f

    private fun sheet() = PaperSheet(hinge, top, depth, length, gravity = 5700f, flipped = false)

    /** Runs a throw toward [target] for at most [maxSeconds]; returns whether it settled. */
    private fun PaperSheet.throwTo(target: Boolean, maxSeconds: Float = 4f): Boolean {
        beginDrive(target)
        var t = 0f
        while (t < maxSeconds) {
            if (advance(1f / 60f, target)) return true
            t += 1f / 60f
        }
        return false
    }

    private fun PaperSheet.assertFront() {
        assertTrue(atRest && !restingFlipped)
        assertEquals(hinge + length, y[PaperSheet.Segments], 0.5f)
        assertEquals(0f, z[PaperSheet.Segments], 0.5f)
    }

    private fun PaperSheet.assertFlipped() {
        assertTrue(atRest && restingFlipped)
        // Wrapped over the binding: the free edge hangs behind the calendar body.
        assertEquals(-depth, z[PaperSheet.Segments], 0.5f)
        assertTrue(y[PaperSheet.Segments] > top)
    }

    @Test
    fun pageFlipsOverAndBackRepeatedly() {
        val sheet = sheet()
        repeat(3) {
            assertTrue(sheet.throwTo(true))
            sheet.assertFlipped()
            assertTrue(sheet.throwTo(false))
            sheet.assertFront()
        }
    }

    @Test
    fun reversingMidThrowStillLandsInTheRequestedState() {
        val sheet = sheet()
        sheet.beginDrive(true)
        repeat(18) { sheet.advance(1f / 60f, true) }
        assertTrue(sheet.throwTo(false))
        sheet.assertFront()

        assertTrue(sheet.throwTo(true))
        sheet.beginDrive(false)
        repeat(20) { sheet.advance(1f / 60f, false) }
        assertTrue(sheet.throwTo(true))
        sheet.assertFlipped()
    }
}
