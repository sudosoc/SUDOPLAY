package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.NetState

/** First launch: pick a language, see what the app does, start. Three short pages, can be skipped. */
@Composable
fun Onboarding(vm: PlayerViewModel, onDone: () -> Unit) {
    val t = LocalSudo.current
    val st = vm.settings
    var page by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(t.bgTop)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDone) { Text("Skip", color = t.text.copy(alpha = 0.7f)) }
            }
            Spacer(Modifier.weight(1f))
            LogoImage(st.appLogo.value, 96.dp)
            Spacer(Modifier.height(18.dp))
            when (page) {
                0 -> {
                    Text("SUDOPLAY", color = t.text, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp))
                    Spacer(Modifier.height(6.dp))
                    Text("Music Player by SUDOSOC", color = t.text.copy(alpha = 0.6f))
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("en" to "English", "ar" to "العربية").forEach { (code, label) ->
                            val on = (st.language.value == code) || (st.language.value == "auto" && Tr.isArabic("auto") == (code == "ar"))
                            M3Chip(label, on) { st.language.set(code) }
                        }
                    }
                }
                1 -> {
                    Tx(if (Tr.ar) "كل مزاجك في مكان واحد" else "Everything you listen to, in one place", 22)
                    Spacer(Modifier.height(16.dp))
                    listOf(
                        "Your music, with synced lyrics and a sound studio" to "موسيقى موبايلك، بكلمات متزامنة واستوديو صوت",
                        "Online search and Discover — no account needed" to "بحث أونلاين واكتشاف — من غير حساب",
                        "Download songs with their cover and lyrics" to "حمّل الأغاني بالغلاف والكلمات",
                        "Themes, fonts, icons and a Home you arrange yourself" to "ثيمات وخطوط وأيقونات وصفحة رئيسية ترتّبها بنفسك",
                    ).forEach { (en, ar) -> Tx("• " + if (Tr.ar) ar else en, 15, 0.8f); Spacer(Modifier.height(8.dp)) }
                }
                else -> {
                    Tx(if (Tr.ar) "جاهز تبدأ؟" else "Ready?", 22)
                    Spacer(Modifier.height(12.dp))
                    Tx(
                        if (Tr.ar) "هنطلب صلاحية قراءة الموسيقى على موبايلك. مفيش حاجة بتتبعت لأي حد، ولو حبيت تقفل أي حاجة روح الإعدادات ← الخصوصية."
                        else "Next, Android will ask to read the music on your phone. Nothing is sent anywhere; you can review everything in Settings → Privacy.", 15, 0.75f,
                    )
                    if (!NetState.online) { Spacer(Modifier.height(10.dp)); Tx(if (Tr.ar) "أنت أوفلاين دلوقتي — التطبيق شغال بالأغاني اللي على موبايلك." else "You're offline right now — your own music still works.", 13, 0.6f) }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                repeat(3) { i -> Box(Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp).clip(RoundedCornerShape(50)).background(if (i == page) t.primary else t.text.copy(alpha = 0.25f))) }
            }
            PillButton(if (page < 2) (if (Tr.ar) "التالي" else "Next") else (if (Tr.ar) "يلا نبدأ" else "Get started"), I.Check, filled = true) { if (page < 2) page++ else onDone() }
        }
    }
}

@Composable
private fun Tx(text: String, size: Int, alpha: Float = 1f) {
    Text(text, color = LocalSudo.current.text.copy(alpha = alpha), fontSize = size.sp, fontWeight = if (size >= 20) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
}

@Composable
private fun M3Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    Text(
        label, color = if (on) t.onAccent else t.text, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary))) else Modifier.border(1.dp, t.text.copy(alpha = 0.3f), RoundedCornerShape(50)))
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 11.dp),
    )
}

/** Shown once after an update. */
@Composable
fun WhatsNew(version: String, onClose: () -> Unit) {
    val en = listOf("Touch and hold any song to select several — then add them to a playlist, the queue, favorites or download them together", "Every menu is now one clean sheet from the bottom of the screen, in your theme, never cut off at an edge", "Streamed songs are kept for instant replay and survive short network drops (size and quality in Settings → Online)", "Offline mode: shows and plays only what is on your phone — it also switches on by itself when the internet drops", "Downloaded files now carry their name, cover and lyrics inside them", "Undo instead of “are you sure?” when you delete a playlist or remove songs")
    val ar = listOf("اضغط مطوّلاً على أي أغنية عشان تحدد أكتر من واحدة — وبعدها ضيفهم لقائمة أو للدور أو للمفضلة أو حمّلهم مع بعض", "كل القوايم بقت ورقة واحدة نضيفة من تحت الشاشة، بنفس ثيمك، ومش بتتقطع من أي ناحية", "الأغاني اللي بتسمعها أونلاين بتتحفظ عشان تشتغل تاني فوراً وتكمّل لو النت قطع شوية (الحجم والجودة من الإعدادات ← أونلاين)", "وضع أوفلاين: بيعرض ويشغّل اللي على موبايلك بس — وبيشتغل لوحده لما النت يقطع", "الملفات المحمّلة بقت شايلة اسمها وغلافها وكلماتها جواها", "تراجع بدل «متأكد؟» لما تمسح قائمة أو تشيل أغاني")
    AlertDialog(
        onDismissRequest = onClose, title = { Text(if (Tr.ar) "الجديد في $version" else "What's new in $version") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { (if (Tr.ar) ar else en).forEach { Text("• $it") } } },
        confirmButton = { TextButton(onClose) { Text("Got it") } },
    )
}

/** A thin bar when the phone has no internet. */
@Composable
fun OfflineBanner() {
    val t = LocalSudo.current
    Text(
        if (com.sudosoc.sudoplay.data.NetState.online) "Offline mode — only songs on this phone will play" else "No internet — only songs on this phone will play", color = t.onAccent, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().background(t.primary.copy(alpha = 0.9f)).padding(vertical = 4.dp),
    )
}
