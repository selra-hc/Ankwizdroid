/*
 *  Copyright (c) 2025 argon2r <vincentcs008@gmail.com>
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

package com.ichi2.anki.pages

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class HtmlQuestionnaireParserTest {
    @Test
    fun `parse single question with correct answer`() {
        val html =
            """
            <html><body>
            <div class="question">
                <span class="question-number">1</span>
                <span class="question-text">What is 2+2?</span>
                <div class="answer" data-correct="true">
                    <span class="answer-letter">A</span>
                    <span class="answer-text">4</span>
                    <span class="justification">Basic arithmetic</span>
                </div>
                <div class="answer">
                    <span class="answer-letter">B</span>
                    <span class="answer-text">3</span>
                    <span class="justification">Incorrect</span>
                </div>
                <div class="answer">
                    <span class="answer-letter">C</span>
                    <span class="answer-text">5</span>
                </div>
                <div class="answer">
                    <span class="answer-letter">D</span>
                    <span class="answer-text">22</span>
                </div>
            </div>
            </body></html>
            """.trimIndent()

        val questions = HtmlQuestionnaireParser.parse(html)
        assertThat(questions.size, equalTo(1))

        val q = questions[0]
        assertThat(q.questionNumber, equalTo("1"))
        assertThat(q.question, equalTo("What is 2+2?"))
        assertThat(q.correctAnswer, equalTo("A"))
        assertThat(q.answers["A"], equalTo("4"))
        assertThat(q.answers["B"], equalTo("3"))
        assertThat(q.answers["C"], equalTo("5"))
        assertThat(q.answers["D"], equalTo("22"))
        assertThat(q.justifications["A"], equalTo("Basic arithmetic"))
        assertThat(q.justifications["B"], equalTo("Incorrect"))
        assertThat(q.justifications["C"], equalTo(""))
        assertThat(q.justifications["D"], equalTo(""))
    }

    @Test
    fun `parse multiple questions`() {
        val html =
            """
            <html><body>
            <div class="question">
                <span class="question-number">1</span>
                <span class="question-text">Question one?</span>
                <div class="answer" data-correct="true">
                    <span class="answer-letter">A</span>
                    <span class="answer-text">Answer A1</span>
                </div>
                <div class="answer">
                    <span class="answer-letter">B</span>
                    <span class="answer-text">Answer B1</span>
                </div>
            </div>
            <div class="question">
                <span class="question-number">2</span>
                <span class="question-text">Question two?</span>
                <div class="answer">
                    <span class="answer-letter">A</span>
                    <span class="answer-text">Answer A2</span>
                </div>
                <div class="answer" data-correct="true">
                    <span class="answer-letter">B</span>
                    <span class="answer-text">Answer B2</span>
                </div>
            </div>
            </body></html>
            """.trimIndent()

        val questions = HtmlQuestionnaireParser.parse(html)
        assertThat(questions.size, equalTo(2))
        assertThat(questions[0].correctAnswer, equalTo("A"))
        assertThat(questions[1].correctAnswer, equalTo("B"))
    }

    @Test
    fun `parse empty HTML returns empty list`() {
        val html = "<html><body></body></html>"
        val questions = HtmlQuestionnaireParser.parse(html)
        assertThat(questions.size, equalTo(0))
    }

    @Test
    fun `parse question without question-text is skipped`() {
        val html =
            """
            <html><body>
            <div class="question">
                <span class="question-number">1</span>
                <div class="answer" data-correct="true">
                    <span class="answer-letter">A</span>
                    <span class="answer-text">Answer</span>
                </div>
            </div>
            </body></html>
            """.trimIndent()

        val questions = HtmlQuestionnaireParser.parse(html)
        assertThat(questions.size, equalTo(0))
    }

    @Test
    fun `parse question without question-number defaults to empty`() {
        val html =
            """
            <html><body>
            <div class="question">
                <span class="question-text">No number?</span>
                <div class="answer" data-correct="true">
                    <span class="answer-letter">A</span>
                    <span class="answer-text">Yes</span>
                </div>
            </div>
            </body></html>
            """.trimIndent()

        val questions = HtmlQuestionnaireParser.parse(html)
        assertThat(questions.size, equalTo(1))
        assertThat(questions[0].questionNumber, equalTo(""))
    }
}
