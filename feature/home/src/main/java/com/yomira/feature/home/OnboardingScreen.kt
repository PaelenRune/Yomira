package com.yomira.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val OnboardingBackground = Color(0xFF06050C)
private val OnboardingPurple = Color(0xFF9D4EDD)
private val OnboardingPink = Color(0xFFFF007F)
private val OnboardingText = Color(0xFFF5F0FF)
private val OnboardingMuted = Color(0xFFC2B7D1)

/**
 * Every string the onboarding wizard renders. The app module fills it from string
 * resources so the wizard already speaks the device language (English default,
 * Turkish via `values-tr`) and re-renders live after a locale switch.
 */
data class OnboardingCopy(
    val stepIndicator: String = "%1\$d / %2\$d",
    val stepDescription: String = "Kurulum adımı %1\$d, toplam %2\$d",
    val languageTitle: String = "Hikâyeni hangi dilde okuyalım?",
    val languageDescription: String = "Kaynak önerilerini okuma diline göre hazırlayalım.",
    val languageTurkish: String = "Türkçe",
    val languageEnglish: String = "English",
    val languageNote: String = "Arayüz ve kaynak önerileri anında seçtiğin dile geçer.",
    val contentTitle: String = "İçerik sınırlarını sen belirle",
    val contentDescription: String = "Yetişkin içerik varsayılan olarak kapalıdır.",
    val adultLabel: String = "Yetişkin içerik (+18)",
    val statusEnabled: String = "Açık",
    val statusDisabled: String = "Kapalı",
    val adultSwitchDescription: String = "Yetişkin içerik izni",
    val levelsTitle: String = "İki farklı içerik seviyesi",
    val levelOne: String = "Level 1 · Hikâye odaklı Mature seriler. MILF Party ve Kimi wa Midara gibi seriler yetişkin temalar içerir.",
    val levelTwo: String = "Level 2 · Saf Hentai / Porhwa. Açık cinsel içerik odaklı seriler yalnızca kilitli Vault alanında yer alır.",
    val adultConsent: String = "Açarak 18 yaşında veya daha büyük olduğunu onaylarsın. Bu izin Vault’un cihaz kilidini kaldırmaz.",
    val sourceTitle: String = "Kaynaklarına bir kapı aç",
    val sourceDescription: String = "Keiyoushi resmi eklenti deposunu tek dokunuşla bağla.",
    val sourceBody: String = "Dil ve içerik tercihlerine uygun eklenti önerileri hazırlanır.",
    val sourcePrivacy: String = "Yalnızca depo listesi indirilir. Hiçbir APK otomatik yüklenmez veya güvenilir olarak işaretlenmez.",
    val sourceRepo: String = "raw.githubusercontent.com/keiyoushi/extensions",
    val sourceSkippable: String = "Depoyu daha sonra bağlamak istersen deposuz başlayabilirsin.",
    val saving: String = "Kurulum kaydediliyor…",
    val connectAndStart: String = "Depoyu Bağla ve Başla",
    val continueLabel: String = "Devam et",
    val skipRepository: String = "Hadi Başlayalım · Deposuz devam et",
    val back: String = "Geri",
)

/** Stateless completion boundary: the host navigates only after persistence succeeds. */
@Composable
fun OnboardingScreen(
    saving: Boolean,
    error: String?,
    onComplete: (languageCode: String, adultEnabled: Boolean, integrateRepository: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    copy: OnboardingCopy = OnboardingCopy(),
    initialLanguage: String = "tr",
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    // Pre-selected from the active app locale so the highlighted option matches the
    // language the wizard is already rendered in.
    var language by rememberSaveable { mutableStateOf(if (initialLanguage == "en") "en" else "tr") }
    var adultEnabled by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step > 0 || saving) { if (!saving) step-- }

    Surface(modifier = modifier.fillMaxSize(), color = OnboardingBackground, contentColor = OnboardingText) {
        Column(Modifier.safeDrawingPadding().widthIn(max = 600.dp).fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Yomira", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text(String.format(copy.stepIndicator, step + 1, 3), color = OnboardingMuted)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.semantics { contentDescription = String.format(copy.stepDescription, step + 1, 3) },
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(Modifier.size(width = if (step == index) 32.dp else 8.dp, height = 8.dp)
                        .background(if (step == index) OnboardingPink else OnboardingPurple.copy(alpha = .3f), CircleShape))
                }
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "Onboarding step",
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally { it / 5 * direction }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally { -it / 5 * direction })
                }) { page ->
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (page) {
                        0 -> {
                            StepHeading(copy.languageTitle, copy.languageDescription)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LanguageOption(copy.languageTurkish, "TR", language == "tr", !saving) { language = "tr" }
                                LanguageOption(copy.languageEnglish, "EN", language == "en", !saving) { language = "en" }
                            }
                            Text(copy.languageNote, color = OnboardingMuted)
                        }
                        1 -> {
                            StepHeading(copy.contentTitle, copy.contentDescription)
                            Surface(color = Color(0xFF171023), shape = RoundedCornerShape(20.dp)) {
                                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(copy.adultLabel, fontWeight = FontWeight.SemiBold)
                                        Text(if (adultEnabled) copy.statusEnabled else copy.statusDisabled, color = OnboardingMuted)
                                    }
                                    Switch(checked = adultEnabled, onCheckedChange = { adultEnabled = it }, enabled = !saving,
                                        modifier = Modifier.semantics { contentDescription = copy.adultSwitchDescription },
                                        colors = SwitchDefaults.colors(checkedTrackColor = OnboardingPurple))
                                }
                            }
                            Surface(color = OnboardingPurple.copy(alpha = .12f), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(copy.levelsTitle, fontWeight = FontWeight.SemiBold)
                                    Text(copy.levelOne, color = OnboardingMuted)
                                    Text(copy.levelTwo, color = OnboardingMuted)
                                }
                            }
                            Text(copy.adultConsent, color = OnboardingMuted)
                        }
                        else -> {
                            StepHeading(copy.sourceTitle, copy.sourceDescription)
                            Surface(color = Color(0xFF171023), shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, OnboardingPurple.copy(alpha = .5f))) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Keiyoushi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(copy.sourceBody, color = OnboardingMuted)
                                    Text(copy.sourcePrivacy, color = OnboardingMuted)
                                    Text(copy.sourceRepo, style = MaterialTheme.typography.bodySmall,
                                        color = OnboardingMuted)
                                }
                            }
                            Text(copy.sourceSkippable, color = OnboardingMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (error != null) {
                Text(error, color = Color(0xFFFFAFCB), modifier = Modifier.padding(vertical = 12.dp)
                    .semantics { stateDescription = error })
            }
            Button(onClick = {
                if (step < 2) step++ else onComplete(language, adultEnabled, true)
            }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OnboardingPink, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp)) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(copy.saving)
                } else Text(if (step == 2) copy.connectAndStart else copy.continueLabel)
            }
            if (step == 2) {
                TextButton(onClick = { onComplete(language, adultEnabled, false) }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth()) { Text(copy.skipRepository, color = OnboardingMuted) }
            }
            if (step > 0) {
                TextButton(onClick = { step-- }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(copy.back, color = OnboardingMuted)
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = OnboardingMuted)
    }
}

@Composable
private fun LanguageOption(title: String, code: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(color = if (selected) OnboardingPurple.copy(alpha = .18f) else Color(0xFF171023),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) OnboardingPurple else Color.Transparent)) {
        Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(code, color = OnboardingMuted, modifier = Modifier.padding(end = 16.dp))
            RadioButton(selected = selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = OnboardingPurple))
        }
    }
}
