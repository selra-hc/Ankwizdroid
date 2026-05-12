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

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.ensureMultipleChoiceNotetype
import org.jsoup.Jsoup
import timber.log.Timber

private const val EXPECTED_FIELD_COUNT = 11

data class McQuestion(
    val questionNumber: String,
    val question: String,
    val answers: Map<String, String>,
    val justifications: Map<String, String>,
    val correctAnswer: String,
)

object HtmlQuestionnaireParser {
    fun parse(html: String): List<McQuestion> {
        val doc = Jsoup.parse(html)
        val questionElements = doc.select("div.question")
        val results = mutableListOf<McQuestion>()

        for (qEl in questionElements) {
            val questionNumber = qEl.selectFirst("span.question-number")?.text()?.trim() ?: ""
            val questionText = qEl.selectFirst("span.question-text")?.text()?.trim() ?: continue

            val answers = mutableMapOf<String, String>()
            val justifications = mutableMapOf<String, String>()
            var correctAnswer = ""

            for (answerEl in qEl.select("div.answer")) {
                val letter =
                    answerEl
                        .selectFirst("span.answer-letter")
                        ?.text()
                        ?.trim()
                        ?.uppercase() ?: continue
                val answerText = answerEl.selectFirst("span.answer-text")?.text()?.trim() ?: continue
                val justification = answerEl.selectFirst("span.justification")?.text()?.trim() ?: ""

                answers[letter] = answerText
                justifications[letter] = justification

                if (answerEl.attr("data-correct").trim().lowercase() == "true") {
                    correctAnswer = letter
                }
            }

            if (questionText.isNotBlank() && answers.isNotEmpty()) {
                results.add(
                    McQuestion(
                        questionNumber = questionNumber,
                        question = questionText,
                        answers = answers,
                        justifications = justifications,
                        correctAnswer = correctAnswer,
                    ),
                )
            }
        }

        Timber.i("HtmlQuestionnaireParser: parsed %d questions from HTML", results.size)
        return results
    }

    suspend fun importQuestions(
        questions: List<McQuestion>,
        deckId: Long,
    ): Int {
        var imported = 0
        withCol {
            val notetypeId = ensureMultipleChoiceNotetype() ?: return@withCol
            val notetype = notetypes.get(notetypeId) ?: return@withCol

            val effectiveNotetype =
                if (notetype.fields.size != EXPECTED_FIELD_COUNT) {
                    Timber.w(
                        "HtmlQuestionnaireParser: existing notetype '%s' has %d fields but %d required; creating new notetype",
                        notetype.name,
                        notetype.fields.size,
                        EXPECTED_FIELD_COUNT,
                    )
                    val nt = notetypes.newMultipleChoiceNotetype()
                    notetypes.add(nt)
                    nt
                } else {
                    notetype
                }

            for (q in questions) {
                try {
                    val note = newNote(effectiveNotetype)
                    note.fields[0] = q.questionNumber
                    note.fields[1] = q.question

                    for (letter in listOf("A", "B", "C", "D")) {
                        val answerIdx = 2 + (letter[0].code - 'A'.code)
                        val justIdx = 6 + (letter[0].code - 'A'.code)
                        note.fields[answerIdx] = q.answers[letter] ?: ""
                        note.fields[justIdx] = q.justifications[letter] ?: ""
                    }

                    note.fields[10] = q.correctAnswer

                    addNote(note, deckId)
                    imported++
                } catch (e: Exception) {
                    Timber.w(
                        e,
                        "HtmlQuestionnaireParser: failed to import question #%s ('%s'): %s",
                        q.questionNumber,
                        q.question.take(50),
                        e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }

        Timber.i("HtmlQuestionnaireParser: imported %d / %d questions", imported, questions.size)
        return imported
    }
}
