package com.joebywan.daybook

import com.joebywan.daybook.puzzles.SquareTap
import com.joebywan.daybook.puzzles.highlightedDigit
import com.joebywan.daybook.puzzles.squareTap
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuSquareTapTest {
    @Test fun `an armed digit enters only with no square selected`() {
        assertEquals(SquareTap.ENTER, squareTap(5, null, 3))
        assertEquals(SquareTap.SELECT, squareTap(5, 7, 3))
    }

    @Test fun `the selected square again lets go, so digit-first can start`() {
        assertEquals(SquareTap.DESELECT, squareTap(5, 5, null))
        assertEquals(SquareTap.DESELECT, squareTap(5, 5, 3))
    }

    @Test fun `with nothing armed a tap selects, as it always has`() {
        assertEquals(SquareTap.SELECT, squareTap(5, null, null))
        assertEquals(SquareTap.SELECT, squareTap(5, 7, null))
    }

    private val cells = List(81) { if (it == 4) 7 else 0 }

    @Test fun `a note press highlights its digit on its own empty square in notes mode`() {
        assertEquals(3, highlightedDigit(cells, 5, null, true, 5, 3))
    }

    @Test fun `a note press does not highlight on another square, out of notes mode or with none made`() {
        assertEquals(0, highlightedDigit(cells, 6, null, true, 5, 3))
        assertEquals(0, highlightedDigit(cells, 5, null, false, 5, 3))
        assertEquals(0, highlightedDigit(cells, 5, null, true, null, 3))
    }

    @Test fun `a filled square and an armed digit beat a note press`() {
        assertEquals(7, highlightedDigit(cells, 4, null, true, 4, 3))
        assertEquals(9, highlightedDigit(cells, null, 9, true, null, 3))
    }
}
