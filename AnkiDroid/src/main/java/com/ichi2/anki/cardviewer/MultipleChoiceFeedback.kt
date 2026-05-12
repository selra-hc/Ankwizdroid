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

import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import timber.log.Timber

data class McAnswer(
    val letter: String,
    val text: String,
    val justification: String,
)

/**
 * Builds HTML for the multiple-choice feedback region on the card back.
 *
 * Templates must contain the literal placeholder [FEEDBACK_PLACEHOLDER] for injection to run.
 * If the placeholder is missing (user-customised template), [injectForCard] returns the input unchanged.
 */
object MultipleChoiceFeedback {
    const val FEEDBACK_PLACEHOLDER: String = """<div id="mc-feedback"></div>"""

    private val mcOptionOpenRegex = Regex("""<div class="mc-option"([^>]*)>""", RegexOption.IGNORE_CASE)
    private val mcOptionDataIdxRegex = Regex("""data-idx="([A-Da-d])"""")
    private val mcOptionOnclickRegex = Regex("""\s*onclick="[^"]*"""")

    data class Strings(
        val right: String,
        val wrong: String,
        val noAnswer: String,
        val correctAnswerLabel: String,
        val yourAnswerLabel: String,
    )

    fun containsFeedbackPlaceholder(html: String): Boolean = html.contains(FEEDBACK_PLACEHOLDER)

    fun escapeHtml(s: String): String =
        buildString(s.length + 8) {
            for (c in s) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }

    fun buildFeedbackHtml(
        answers: List<McAnswer>,
        correctLetter: String,
        selectedLetter: String?,
        strings: Strings,
    ): String {
        val correct = answers.find { it.letter == correctLetter } ?: return ""
        val esc = ::escapeHtml

        fun correctBlock(): String =
            """<div class="mc-correct-answer"><b>${esc(strings.correctAnswerLabel)}</b> ${esc(correct.text)}""" +
                """<div class="mc-just-correct">${esc(correct.justification)}</div></div>"""

        val sel = selectedLetter?.takeIf { it in setOf("A", "B", "C", "D") }
        return when {
            sel == null -> {
                """<div class="mc-wrong">${esc(strings.noAnswer)}</div>""" + correctBlock()
            }
            sel == correctLetter -> {
                """<div class="mc-correct">${esc(strings.right)}</div>""" +
                    """<div class="mc-just-correct">${esc(correct.justification)}</div>"""
            }
            else -> {
                val wrongAns = answers.find { it.letter == sel }
                val wrongText = wrongAns?.text.orEmpty()
                val wrongJust = wrongAns?.justification.orEmpty()
                """<div class="mc-wrong">${esc(strings.wrong)}</div>""" +
                    """<div class="mc-your-answer"><b>${esc(strings.yourAnswerLabel)}</b> ${esc(wrongText)}""" +
                    """<div class="mc-just-wrong">${esc(wrongJust)}</div></div>""" +
                    correctBlock()
            }
        }
    }

    fun injectInto(
        html: String,
        feedbackInnerHtml: String,
    ): String = html.replace(FEEDBACK_PLACEHOLDER, """<div id="mc-feedback">$feedbackInnerHtml</div>""")

    /**
     * Strips [onclick] from each `.mc-option` row (so the answer side cannot re-fire mc-select) and
     * adds highlight classes: correct key, and optional selected-right / selected-wrong for the
     * tapped letter when [selectedLetter] is non-null.
     *
     * No-op when [html] does not contain `id="mc-options"`.
     */
    fun decorateMcOptionsInBackHtml(
        html: String,
        selectedLetter: String?,
        correctLetter: String,
    ): String {
        if (!html.contains("""id="mc-options"""")) return html
        val sel = selectedLetter?.uppercase()?.takeIf { it in setOf("A", "B", "C", "D") }
        val corr = correctLetter.uppercase()
        return mcOptionOpenRegex.replace(html) { match ->
            val attrBlock = match.groupValues[1]
            val letter =
                mcOptionDataIdxRegex
                    .find(attrBlock)
                    ?.groupValues
                    ?.get(1)
                    ?.uppercase()
                    ?: return@replace match.value
            val withoutOnclick = mcOptionOnclickRegex.replace(attrBlock, "")
            val extras =
                buildList {
                    if (letter == corr) add("mc-option-correct")
                    if (sel != null && letter == sel) {
                        add(if (sel == corr) "mc-option-selected-right" else "mc-option-selected-wrong")
                    }
                }
            val stripped = withoutOnclick.trim()
            val classValue =
                buildString {
                    append("mc-option")
                    for (e in extras) {
                        append(' ').append(e)
                    }
                }
            val spacer = if (stripped.isEmpty()) "" else " "
            """<div class="$classValue"$spacer$stripped>"""
        }
    }

    /**
     * If [html] contains the MC feedback placeholder and the note has the expected 11 fields,
     * replaces the placeholder with feedback. Otherwise returns [html] unchanged.
     */
    fun injectForCard(
        col: Collection,
        card: Card,
        html: String,
        selectedLetter: String?,
        strings: Strings,
    ): String {
        if (!containsFeedbackPlaceholder(html)) return html
        val note =
            try {
                card.note(col)
            } catch (e: Exception) {
                Timber.w(e, "MultipleChoiceFeedback: could not load note")
                return html
            }
        if (note.fields.size < 11) return html

        val correctRaw = note.fields[10].trim().uppercase()
        if (correctRaw.length != 1 || correctRaw !in setOf("A", "B", "C", "D")) {
            Timber.w("MultipleChoiceFeedback: invalid CorrectAnswer field %s", note.fields[10].take(20))
            return html
        }

        val answers =
            listOf("A", "B", "C", "D").map { letter ->
                val i = letter[0].code - 'A'.code
                McAnswer(
                    letter = letter,
                    text = note.fields[2 + i].trim(),
                    justification = note.fields[6 + i].trim(),
                )
            }

        val inner = buildFeedbackHtml(answers, correctRaw, selectedLetter?.uppercase(), strings)
        if (inner.isEmpty()) return html
        val withFeedback = injectInto(html, inner)
        return decorateMcOptionsInBackHtml(withFeedback, selectedLetter?.uppercase(), correctRaw)
    }
}
