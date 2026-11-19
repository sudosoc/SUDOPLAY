package com.sudosoc.sudoplay.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** UI language. Arabic is a dictionary over the English text, so anything not translated yet simply stays English. */
object Tr {
    var ar by mutableStateOf(false)

    fun t(s: String): String {
        if (!ar || s.isEmpty()) return s
        AR_WORDS[s]?.let { return it }
        for ((rx, out) in AR_RULES) if (rx.matches(s)) return rx.replace(s, out)
        return s
    }
    /** "auto" follows the phone's language. */
    fun isArabic(setting: String, deviceLang: String = java.util.Locale.getDefault().language) = setting == "ar" || (setting == "auto" && deviceLang == "ar")
}

/** Same as Material's Text for plain strings, but shows the Arabic text when the app is in Arabic. Every screen in this package picks this one up. */
@Composable
fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current,
) = M3Text(
    Tr.t(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight,
    overflow, softWrap, maxLines, minLines, onTextLayout, style,
)
