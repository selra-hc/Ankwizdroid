/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.app.TaskStackBuilder
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.pages.HtmlQuestionnaireParser
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

// see also:
// ImportFileSelectionFragment - selects 'APKG/COLPKG/CSV' and opens a file picker
// onSelectedPackageToImport/onSelectedCsvForImport
// importUtils - copying selected file into local cache
// ImportDialog - confirmation screen after file copied to cache
//    * ImportDialogListener - AnkiActivity implementation of handler for the confirmation screen
//    * AnkiActivity.importAdd/importReplace - called from confirmation screen
// BackendBackups/BackendImporting - new backend for importing
// importReplaceListener - old backend listener for importing

fun interface ImportColpkgListener {
    fun onImportColpkg(colpkgPath: String?)
}

@NeedsTest("successful import from the app menu")
fun AnkiActivity.onSelectedPackageToImport(data: Intent) {
    when (val importResult = ImportUtils.handleFileImport(this, data)) {
        is ImportResult.Failure ->
            runOnUiThread {
                ImportUtils.showImportUnsuccessfulDialog(this, importResult, exitActivity = false)
            }
        is ImportResult.Success -> {
            // a Message was posted, don't wait for onResume to process it
            if (this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                dialogHandler.popMessage()?.let { dialogHandler.sendStoredMessage(it) }
            }
        }
    }
}

fun Activity.onSelectedCsvForImport(data: Intent) {
    val path = ImportUtils.getFileCachedCopy(this, data) ?: return
    val csvImporterIntent = CsvImporter.getIntent(this, path)

    val stackBuilder = TaskStackBuilder.create(this)
    stackBuilder.addNextIntentWithParentStack(Intent(this, DeckPicker::class.java))
    stackBuilder.addNextIntent(csvImporterIntent)

    stackBuilder.startActivities()
}

fun AnkiActivity.onSelectedHtmlForImport(data: Intent) {
    val path = ImportUtils.getFileCachedCopy(this, data) ?: return
    applicationScope.launch {
        try {
            val html = withContext(Dispatchers.IO) { File(path).readText() }
            val questions = HtmlQuestionnaireParser.parse(html)
            if (questions.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@onSelectedHtmlForImport, R.string.import_html_no_questions, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val deckId = withCol { decks.selected() }
            val total = questions.size
            val imported = HtmlQuestionnaireParser.importQuestions(questions, deckId)
            withContext(Dispatchers.Main) {
                val msg =
                    if (imported == total) {
                        getString(R.string.import_html_success, imported)
                    } else {
                        getString(R.string.import_html_partial, imported, total)
                    }
                Toast.makeText(this@onSelectedHtmlForImport, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.w(e, "HTML import failed")
            withContext(Dispatchers.Main) {
                Toast
                    .makeText(
                        this@onSelectedHtmlForImport,
                        getString(R.string.import_html_error, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }
}

fun AnkiActivity.showImportDialog(
    id: ImportDialog.Type,
    importPath: String,
) {
    Timber.d("showImportDialog() delegating to ImportDialog")
    val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, importPath)
    showAsyncDialogFragment(newFragment)
}

fun AnkiActivity.showImportDialog() {
    showImportDialog(
        ImportOptions(
            importApkg = true,
            importColpkg = true,
            importTextFile = true,
            importHtmlFile = true,
        ),
    )
}

fun AnkiActivity.showImportDialog(options: ImportOptions) {
    showDialogFragment(ImportFileSelectionFragment.newInstance(options))
}

class DatabaseRestorationListener(
    val activity: AnkiActivity,
    val newAnkiDroidDirectory: File,
) : ImportColpkgListener {
    override fun onImportColpkg(colpkgPath: String?) {
        Timber.i("Database restoration correct")
        activity.sharedPrefs().edit {
            putString("deckPath", newAnkiDroidDirectory.absolutePath)
        }
        activity.dismissAllDialogFragments()
        activity.importColpkgListener = null
        CollectionHelper.ankiDroidDirectoryOverride = null
    }
}
