/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test

class MultipleChoiceFeedbackTest {
    private val strings =
        MultipleChoiceFeedback.Strings(
            right = "Right",
            wrong = "Wrong",
            noAnswer = "No answer",
            correctAnswerLabel = "Correct:",
            yourAnswerLabel = "Yours:",
        )

    private val sampleAnswers =
        listOf(
            McAnswer("A", "Alpha", "Because A"),
            McAnswer("B", "Beta", "Because B"),
            McAnswer("C", "Gamma", "Because C"),
            McAnswer("D", "Delta", "Because D"),
        )

    @Test
    fun `right answer shows right label and justification`() {
        val html = MultipleChoiceFeedback.buildFeedbackHtml(sampleAnswers, "B", "B", strings)
        assertThat(html, containsString("Right"))
        assertThat(html, containsString("Because B"))
        assertThat(html, not(containsString("Yours:")))
    }

    @Test
    fun `wrong answer shows wrong your answer and correct block`() {
        val html = MultipleChoiceFeedback.buildFeedbackHtml(sampleAnswers, "B", "D", strings)
        assertThat(html, containsString("Wrong"))
        assertThat(html, containsString("Yours:"))
        assertThat(html, containsString("Delta"))
        assertThat(html, containsString("Because D"))
        assertThat(html, containsString("Correct:"))
        assertThat(html, containsString("Beta"))
        assertThat(html, containsString("Because B"))
    }

    @Test
    fun `no selection shows no answer and correct block`() {
        val html = MultipleChoiceFeedback.buildFeedbackHtml(sampleAnswers, "C", null, strings)
        assertThat(html, containsString("No answer"))
        assertThat(html, containsString("Correct:"))
        assertThat(html, containsString("Gamma"))
    }

    @Test
    fun `escapeHtml escapes angle brackets`() {
        assertThat(MultipleChoiceFeedback.escapeHtml("<b>x</b>"), equalTo("&lt;b&gt;x&lt;/b&gt;"))
    }

    @Test
    fun `injectInto replaces placeholder`() {
        val before = """<hr><div id="mc-feedback"></div>"""
        val after = MultipleChoiceFeedback.injectInto(before, "<span>ok</span>")
        assertThat(after, equalTo("""<hr><div id="mc-feedback"><span>ok</span></div>"""))
    }

    @Test
    fun `containsFeedbackPlaceholder detects placeholder`() {
        assertThat(MultipleChoiceFeedback.containsFeedbackPlaceholder("x<div id=\"mc-feedback\"></div>y"), equalTo(true))
        assertThat(MultipleChoiceFeedback.containsFeedbackPlaceholder("no"), equalTo(false))
    }
}
