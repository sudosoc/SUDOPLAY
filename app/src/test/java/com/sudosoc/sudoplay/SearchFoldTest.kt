package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.ui.foldForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFoldTest {
    @Test fun arabicVariantsMatch() {
        assertEquals(foldForSearch("إِبراهيم"), foldForSearch("ابراهيم"))
        assertEquals(foldForSearch("سُورَةُ الفاتحة"), foldForSearch("سوره الفاتحه"))
        assertEquals(foldForSearch("مُوسَى"), foldForSearch("موسي"))
    }
    @Test fun latinIsCaseInsensitive() = assertTrue(foldForSearch("Hello World").contains(foldForSearch("WORLD")))
}
