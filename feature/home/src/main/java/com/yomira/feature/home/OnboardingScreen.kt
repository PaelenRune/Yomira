package com.yomira.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yomira.core.model.blacklistTags

private val OnboardingBackground = Color.Black
private val OnboardingPurple = Color(0xFF7B61FF)
private val OnboardingText = Color(0xFFF5F5FA)
private val OnboardingMuted = Color(0xFFB5B5C5)

/** The host supplies localized resources independently of the reading language. */
@Immutable
data class OnboardingCopy(
    val languageTitle: String = "Hangi dillerdeki serileri okumak istersiniz?",
    val languageDescription: String = "Kaynakları ve önerileri okuma diline göre hazırlayalım.",
    val languageTurkish: String = "Türkçe",
    val languageEnglish: String = "English",
    val languageBoth: String = "Her İkisi (Both)",
    val languageNote: String = "Kaynakların hazır. Dil tercihini daha sonra değiştirebilirsin.",
    val saving: String = "Kurulum kaydediliyor…",
    val connectAndStart: String = "Başla ve Keşfet",
)

/** Four explicit steps; no age or content consent is preselected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    saving: Boolean,
    error: String?,
    onComplete: (languageCode: String, isAdult: Boolean, excludedTags: Set<String>, favoriteGenres: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    copy: OnboardingCopy = OnboardingCopy(),
    initialLanguage: String = "tr",
    initialIsAdult: Boolean? = null,
    initialExcludedTags: Set<String> = emptySet(),
    initialFavoriteGenres: Set<String> = emptySet(),
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var isAdult by rememberSaveable { mutableStateOf(initialIsAdult) }
    var excluded by rememberSaveable { mutableStateOf(ArrayList(initialExcludedTags)) }
    var favorites by rememberSaveable { mutableStateOf(ArrayList(initialFavoriteGenres)) }
    var language by rememberSaveable {
        mutableStateOf(initialLanguage.takeIf { it in setOf("tr", "en", "both") } ?: "tr")
    }
    BackHandler(enabled = saving) { }
    BackHandler(enabled = !saving && step > 0) { step-- }

    Surface(modifier = modifier.fillMaxSize(), color = OnboardingBackground, contentColor = OnboardingText) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("Yomira", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${step + 1} / 4", color = OnboardingMuted, modifier = Modifier.padding(top = 12.dp))
                LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = OnboardingPurple)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center) {
                    Spacer(Modifier.height(32.dp))
                    Text(when (step) {
                        0 -> copy.languageTitle
                        1 -> "Yaşına uygun bir başlangıç"
                        2 -> "Neleri görmek istemezsin?"
                        else -> "Hangi dünyalara dalalım?"
                    }, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(when (step) {
                        0 -> copy.languageDescription
                        1 -> "Güvenli mod yetişkin içeriklerini gizler. Tercihini ayarlardan değiştirebilirsin."
                        2 -> "Seçtiğin etiketlerdeki seriler arama, raflar ve Vault dahil hiçbir yerde gösterilmez."
                        else -> "Birden fazla tür seçebilirsin. Bu tercihler kara listeni değiştirmez."
                    }, style = MaterialTheme.typography.bodyLarge, color = OnboardingMuted)
                    Spacer(Modifier.height(32.dp))
                    if (step == 0) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanguageOption(copy.languageTurkish, "TR", language == "tr", !saving) { language = "tr" }
                        LanguageOption(copy.languageEnglish, "EN", language == "en", !saving) { language = "en" }
                        LanguageOption(copy.languageBoth, "TR + EN", language == "both", !saving) { language = "both" }
                    }
                    if (step == 1) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanguageOption("🔞 18 Yaşından Büyüğüm", "18+", isAdult == true, !saving) { isAdult = true }
                        LanguageOption("🛡️ 18 Yaşından Küçüğüm", "Güvenli Mod", isAdult == false, !saving) { isAdult = false }
                    }
                    if (step >= 2) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = if (step == 2) blacklistTags else linkedMapOf(
                            "action" to "Action", "isekai" to "Isekai", "webtoon" to "Webtoon",
                            "murim" to "Murim", "romance" to "Romance", "seinen" to "Seinen",
                        )
                        options.forEach { (key, label) ->
                            val selection = if (step == 2) excluded else favorites
                            FilterChip(selected = key in selection, enabled = !saving,
                                onClick = {
                                    val next = ArrayList(selection).apply { if (!remove(key)) add(key) }
                                    if (step == 2) excluded = next else favorites = next
                                }, label = { Text(if (step == 2) "🚫 $label" else label) })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(copy.languageNote, style = MaterialTheme.typography.bodyMedium, color = OnboardingMuted)
                    Spacer(Modifier.height(32.dp))
                }
                if (error != null) {
                    Text(error, color = Color(0xFFFFB4AB), modifier = Modifier.padding(vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (step > 0) TextButton(onClick = { step-- }, enabled = !saving) { Text("Geri") }
                Button(onClick = {
                    if (step < 3) step++ else onComplete(language, isAdult == true, excluded.toSet(), favorites.toSet())
                }, enabled = !saving && (step != 1 || isAdult != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OnboardingPurple, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp)) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(copy.saving)
                    } else Text(if (step == 3) copy.connectAndStart else "Devam et")
                }
            }
        }
    }
}

@Composable
private fun LanguageOption(title: String, code: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(color = if (selected) Color(0xFF211B3D) else Color(0xFF181820),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) OnboardingPurple else Color(0xFF2A2A36))) {
        Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(code, style = MaterialTheme.typography.labelMedium, color = OnboardingMuted)
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = OnboardingPurple,
                    unselectedColor = OnboardingMuted))
        }
    }
}
