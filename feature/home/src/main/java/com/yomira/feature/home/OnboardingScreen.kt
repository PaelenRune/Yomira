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
import com.yomira.core.model.Manga
import com.yomira.domain.extension.FavoriteSeries

private val OnboardingBackground = Color.Black
private val OnboardingPurple = Color(0xFF7B61FF)
private val OnboardingText = Color(0xFFF5F5FA)
private val OnboardingMuted = Color(0xFFB5B5C5)

/** The host supplies localized resources for the active application language. */
@Immutable
data class OnboardingCopy(
    val languageTitle: String = "Select your reading language",
    val languageDescription: String = "We ask so Yomira can pick sources and suggestions in the languages you actually read. It shapes Home, Discover and search.",
    val languageTurkish: String = "Türkçe",
    val languageEnglish: String = "English",
    val languageBoth: String = "Both (Türkçe + English)",
    val languageNote: String = "You can change your language preference later.",
    val saving: String = "Saving setup…",
    val connectAndStart: String = "Start Exploring",
    val ageTitle: String = "An age-appropriate start",
    val ageDescription: String = "Safe mode shows all-ages series only. Choosing 18+ also adds mature-themed (16+) titles to Home, Discover and search; explicit series stay locked inside the +18 Vault.",
    val exclusionsTitle: String = "What would you rather not see?",
    val exclusionsDescription: String = "We ask so these themes never reach your feeds. Series with these tags are hidden everywhere, including search, shelves and the Vault.",
    val genresTitle: String = "Choose your 5 favorite series",
    val genresDescription: String = "These shape your Home shelves and recommendations. Pick up to 5 beloved series, or skip for now.",
    val seriesSearch: String = "Search series",
    val seriesEmpty: String = "No series found. Try another title or skip for now.",
    val removeFavorite: String = "Remove",
    val skip: String = "Skip",
    val adult: String = "🔞 I am 18 or older",
    val minor: String = "🛡️ I am under 18",
    val safeMode: String = "Safe Mode",
    val gore: String = "Gore / Extreme Violence",
    val back: String = "Back",
    val continueAction: String = "Continue",
    val repositoriesTitle: String = "Content sources",
    val repositoriesDescription: String = "Yomira ships with curated repositories built in: universal Keiyoushi plus the Turkish and Western slices. Install extensions from the Extensions screen; nothing is installed automatically.",
    val addRepository: String = "Add repository",
    val openRepository: String = "Open repository page",
    val confirmRepository: String = "Add the official repository?",
    val cancel: String = "Cancel",
)

/** Four survey steps and an optional repository step; no age or content consent is preselected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    saving: Boolean,
    error: String?,
    onComplete: (languageCode: String, isAdult: Boolean, excludedTags: Set<String>, favoriteGenres: Set<String>, favoriteSeries: List<FavoriteSeries>) -> Unit,
    modifier: Modifier = Modifier,
    copy: OnboardingCopy = OnboardingCopy(),
    initialLanguage: String = "en",
    initialIsAdult: Boolean? = null,
    initialExcludedTags: Set<String> = emptySet(),
    initialFavoriteGenres: Set<String> = emptySet(),
    onLanguageSelected: (String) -> Unit = {},
    onCompleteWithRepository: (String, Boolean, Set<String>, Set<String>, List<FavoriteSeries>) -> Unit = onComplete,
    onOpenRepository: () -> Unit = {},
    showRepositoryStep: Boolean = true,
    initialFavoriteSeries: List<FavoriteSeries> = emptyList(),
    catalog: List<Manga> = emptyList(),
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var isAdult by rememberSaveable { mutableStateOf(initialIsAdult) }
    var excluded by rememberSaveable { mutableStateOf(ArrayList(initialExcludedTags)) }
    var confirmRepository by rememberSaveable { mutableStateOf(false) }
    val totalSteps = if (showRepositoryStep) 5 else 4
    var favorites by rememberSaveable(stateSaver = FavoriteSeriesSaver) { mutableStateOf(initialFavoriteSeries.take(5)) }
    var language by rememberSaveable {
        mutableStateOf(initialLanguage.takeIf { it in setOf("tr", "en", "both") } ?: "en")
    }
    BackHandler(enabled = saving) { }
    BackHandler(enabled = !saving && step > 0) { step-- }

    Surface(modifier = modifier.fillMaxSize(), color = OnboardingBackground, contentColor = OnboardingText) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("Yomira", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${step + 1} / $totalSteps", color = OnboardingMuted, modifier = Modifier.padding(top = 12.dp))
                LinearProgressIndicator(progress = { (step + 1) / totalSteps.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = OnboardingPurple)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center) {
                    Spacer(Modifier.height(32.dp))
                    Text(when (step) {
                        0 -> copy.languageTitle
                        1 -> copy.ageTitle
                        2 -> copy.exclusionsTitle
                        3 -> copy.genresTitle
                        else -> copy.repositoriesTitle
                    }, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(when (step) {
                        0 -> copy.languageDescription
                        1 -> copy.ageDescription
                        2 -> copy.exclusionsDescription
                        3 -> copy.genresDescription
                        else -> copy.repositoriesDescription
                    }, style = MaterialTheme.typography.bodyLarge, color = OnboardingMuted)
                    Spacer(Modifier.height(32.dp))
                    if (step == 0) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanguageOption(copy.languageTurkish, "TR", language == "tr", !saving) { language = "tr"; onLanguageSelected(language) }
                        LanguageOption(copy.languageEnglish, "EN", language == "en", !saving) { language = "en"; onLanguageSelected(language) }
                        LanguageOption(copy.languageBoth, "TR + EN", language == "both", !saving) { language = "both"; onLanguageSelected(language) }
                    }
                    if (step == 1) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanguageOption(copy.adult, "18+", isAdult == true, !saving) { isAdult = true }
                        LanguageOption(copy.minor, copy.safeMode, isAdult == false, !saving) { isAdult = false }
                    }
                    if (step == 2) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blacklistTags.forEach { (key, label) ->
                            val selection = excluded
                            FilterChip(selected = key in selection, enabled = !saving,
                                onClick = {
                                    val next = ArrayList(selection).apply { if (!remove(key)) add(key) }
                                    excluded = next
                                }, label = { Text("🚫 ${if (key == "gore") copy.gore else label}") })
                        }
                    }
                    if (step == 3) FavoriteSeriesPicker(
                        publicCatalog(catalog, excluded.toSet(), isAdult == true), favorites, !saving, copy,
                    ) { favorites = it }
                    Spacer(Modifier.height(20.dp))
                    if (step != 4) Text(copy.languageNote, style = MaterialTheme.typography.bodyMedium, color = OnboardingMuted)
                    Spacer(Modifier.height(32.dp))
                }
                if (error != null) {
                    Text(error, color = Color(0xFFFFB4AB), modifier = Modifier.padding(vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (step > 0) TextButton(onClick = { step-- }, enabled = !saving) { Text(copy.back) }
                if (step == 4) {
                    Button(onClick = { confirmRepository = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(copy.addRepository) }
                    TextButton(onClick = onOpenRepository, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(copy.openRepository) }
                }
                if (step == 3) TextButton(onClick = {
                    favorites = emptyList()
                    if (showRepositoryStep) step++
                    else onComplete(language, isAdult == true, excluded.toSet(), initialFavoriteGenres, emptyList())
                }, enabled = !saving) { Text(copy.skip) }
                Button(onClick = {
                    if (step < totalSteps - 1) step++ else onComplete(language, isAdult == true, excluded.toSet(), initialFavoriteGenres, favorites)
                }, enabled = !saving && (step != 1 || isAdult != null) && (step != 3 || favorites.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OnboardingPurple, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp)) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(copy.saving)
                    } else Text(if (step == 4) copy.skip else if (step == totalSteps - 1) copy.connectAndStart else copy.continueAction)
                }
            }
        }
    }
    if (confirmRepository) AlertDialog(
        onDismissRequest = { confirmRepository = false },
        title = { Text(copy.confirmRepository) },
        confirmButton = { TextButton(onClick = { confirmRepository = false; onCompleteWithRepository(language, isAdult == true, excluded.toSet(), initialFavoriteGenres, favorites) }) { Text(copy.addRepository) } },
        dismissButton = { TextButton(onClick = { confirmRepository = false }) { Text(copy.cancel) } },
    )
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
