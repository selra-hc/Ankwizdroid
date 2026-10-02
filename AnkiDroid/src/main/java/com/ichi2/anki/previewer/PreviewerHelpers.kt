/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Context
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.updateLayoutParams
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` and `ankidroid-cardviewer.js` are added
 *
 * Aimed to be used only for reviewing/previewing cards
 *
 * @param extraJsAssets paths of additional Javascript assets
 * in the `android_assets` folder to be included
 */
@Language("HTML")
fun stdHtml(
    context: Context = AnkiDroidApp.instance,
    extraJsAssets: List<String> = emptyList(),
    nightMode: Boolean = false,
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"
    val baseTheme = if (nightMode) "dark" else "light"
    val docClass = if (nightMode) "night-mode" else ""
    val rootNightMode = if (nightMode) "[class*=night-mode]" else ""

    val canvasColor = ThemeUtils.getThemeAttrColor(context, android.R.attr.colorBackground).toRGBHex()
    val fgColor = ThemeUtils.getThemeAttrColor(context, android.R.attr.textColor).toRGBHex()
    val colors = ":root$rootNightMode { --canvas: $canvasColor; --fg: $fgColor; }"

    val jsAssets: List<String> =
        listOf(
            "backend/js/jquery.min.js",
            "backend/js/mathjax.js",
            "backend/js/vendor/mathjax/tex-chtml-full.js",
            "backend/js/reviewer.js",
            "scripts/ankidroid-cardviewer.js",
        ) + extraJsAssets
    val jsTxt =
        jsAssets.joinToString("\n") {
            """<script src="file:///android_asset/$it"></script>"""
        }

    return """
        <!DOCTYPE html>
        <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
        <head>
            <title>AnkiDroid</title>
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/root-vars.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/ankidroid.css">
            <style>
                .night-mode button { --canvas: #606060; --fg: #eee; }
                $colors
                .night_mode .mc-option { background: #1e1e1e !important; border-color: #555 !important; }
                .night_mode .mc-option:hover { background: #2e2e2e !important; border-color: #777 !important; }
                .night_mode .mc-option-correct { background: #1b3a1b !important; border-color: #4caf50 !important; }
                .night_mode .mc-option-selected-right { background: #2e5a2e !important; border-color: #4caf50 !important; }
                .night_mode .mc-option-selected-wrong { background: #3a1b1b !important; border-color: #ef5350 !important; }
                .night_mode .mc-option-tapped { background: #333 !important; border-color: #888 !important; }
                .night_mode .mc-correct-answer { background: #1b3a1b !important; border-left-color: #4caf50 !important; }
                .night_mode .mc-your-answer { background: #3a1b1b !important; border-left-color: #ef5350 !important; }
                .night_mode .mc-correct { color: #81c784 !important; }
                .night_mode .mc-wrong { color: #ef9a9a !important; }
                .night_mode .mc-just-correct { color: #81c784 !important; }
                .night_mode .mc-just-wrong { color: #ef9a9a !important; }
                .ankidroid_dark_mode .mc-option { background: #383838 !important; border-color: #555 !important; }
                .ankidroid_dark_mode .mc-option:hover { background: #484848 !important; border-color: #777 !important; }
                .ankidroid_dark_mode .mc-option-correct { background: #1e3e1e !important; border-color: #4caf50 !important; }
                .ankidroid_dark_mode .mc-option-selected-right { background: #2e5a2e !important; border-color: #4caf50 !important; }
                .ankidroid_dark_mode .mc-option-selected-wrong { background: #3e1e1e !important; border-color: #ef5350 !important; }
                .ankidroid_dark_mode .mc-option-tapped { background: #484848 !important; border-color: #888 !important; }
                .ankidroid_dark_mode .mc-correct-answer { background: #1e3e1e !important; border-left-color: #4caf50 !important; }
                .ankidroid_dark_mode .mc-your-answer { background: #3e1e1e !important; border-left-color: #ef5350 !important; }
            </style>
        </head>
        <body class="${bodyClass()}">
            <div id="qa"></div>
            $jsTxt
        </body>
        </html>
        """.trimIndent()
}

/**
 * "mathjax-rendered" is a legacy class kept only to support old note types.
 *
 * @return body classes used when showing a card
 */
fun bodyClassForCardOrd(
    cardOrd: CardOrdinal,
    nightMode: Boolean = Themes.isNightTheme,
): String = "card card${cardOrd + 1} ${bodyClass(nightMode)} mathjax-rendered"

private fun bodyClass(nightMode: Boolean = Themes.isNightTheme): String {
    if (!nightMode) return ""
    val classes = StringBuilder("nightMode night_mode")
    if (Themes.currentTheme == NightTheme.DARK) {
        classes.append(" ankidroid_dark_mode")
    }
    return classes.toString()
}

fun MaterialCardView.setFrameStyle() {
    if (Prefs.frameStyle == FrameStyle.BOX && Prefs.isNewStudyScreenEnabled) {
        updateLayoutParams<MarginLayoutParams> {
            leftMargin = 0
            rightMargin = 0
        }
        cardElevation = 0F
        shapeAppearanceModel = ShapeAppearanceModel() // Remove corners
    }
}
