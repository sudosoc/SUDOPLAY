package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.ui.Tr
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TrTest {
    @After fun reset() { Tr.ar = false }

    @Test fun englishIsUntouched() { Tr.ar = false; assertEquals("Downloads", Tr.t("Downloads")) }
    @Test fun arabicWordsAndRules() {
        Tr.ar = true
        assertEquals("التحميلات", Tr.t("Downloads"))
        assertEquals("42 أغنية", Tr.t("42 songs"))
        assertEquals("تم تحديد 3", Tr.t("3 selected"))
        assertEquals("12 أغنية • 1 h 5 min • بتشتغل من غير نت • 80 MB", Tr.t("12 songs • 1 h 5 min • works offline • 80 MB"))
    }
    @Test fun unknownStaysEnglish() { Tr.ar = true; assertEquals("Blinding Lights", Tr.t("Blinding Lights")) }
    @Test fun deviceLanguageAuto() {
        assertTrue(Tr.isArabic("auto", "ar")); assertFalse(Tr.isArabic("auto", "en"))
        assertTrue(Tr.isArabic("ar", "en")); assertFalse(Tr.isArabic("en", "ar"))
    }
}
