package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.i18n.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TeachingLocalizationTest {
    @Test fun `a placement keeps English reasoning and carries its display messages`() {
        val nudge = message("puzzle.sudoku.tutorial.2.retry")
        val explanation = message("puzzle.sudoku.tutorial.2.done")
        val step = PlacementStep("test", 0, 6, setOf(0), emptySet(), nudge, explanation)
        val deduction = Deduction(step, applyTo = { it }, reachedBy = { false })

        assertEquals("Tap the glowing square in row 1, then the 6.", step.nudge)
        assertEquals("Row 1 is complete.", step.explanation)
        assertSame(nudge, deduction.nudgeMessage)
        assertSame(explanation, deduction.explanationMessage)
        assertEquals("Строка 1 заполнена.", deduction.explanationMessage.resolve("ru-RU"))
    }

    @Test fun `legacy steps retain their caller supplied English`() {
        val step = object : TeachStep {
            override val technique = "fixture"
            override val nudge = "Look here."
            override val explanation = "A fixture explanation."
            override val focus = emptySet<Int>()
            override val cited = emptySet<Int>()
            override val targets = emptySet<Int>()
        }
        val deduction = Deduction(step, applyTo = { it }, reachedBy = { false })
        assertEquals(step.nudge, deduction.nudge)
        assertEquals(step.explanation, deduction.explanationMessage.resolve("ru"))
    }
}
