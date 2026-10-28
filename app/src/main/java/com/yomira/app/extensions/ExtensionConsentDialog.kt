package com.yomira.app.extensions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Same AlertDialog/confirm/dismiss pattern as the extensions repository confirmation. */
data class ExtensionConsentCopy(
    val title: String = "Kaynak izni",
    val body: String = "Bu kaynağı ilk kez kullanıyorsun — izin ver / reddet",
    val publisher: String = "Yayıncı sertifikası (SHA-256)",
    val unknown: String = "doğrulanamadı",
    val warning: String = "Sertifika parmak izi güvenlik garantisi değildir. Eklenti kodu uygulama izinleriyle çalışabilir.",
    val allow: String = "İzin ver",
    val decline: String = "Reddet",
) {
    companion object {
        fun forLanguage(language: String) = if (language == "tr") ExtensionConsentCopy() else ExtensionConsentCopy(
            "Source permission", "You are using this source for the first time — allow / decline",
            "Publisher certificate (SHA-256)", "unverified",
            "A certificate fingerprint is not a safety guarantee. Extension code may run with app permissions.",
            "Allow", "Decline")
    }
}

@Composable
fun ExtensionConsentDialog(coordinator: ExtensionConsentCoordinator) {
    val pending by coordinator.prompt.collectAsStateWithLifecycle()
    val prompt = pending ?: return
    val language = if (LocalConfiguration.current.locales[0].language == "tr") "tr" else "en"
    val copy = ExtensionConsentCopy.forLanguage(language)
    val fingerprint = prompt.manifest.publisherFingerprint?.chunked(2)?.take(8)?.joinToString(":")?.plus("…") ?: copy.unknown
    AlertDialog(onDismissRequest = { coordinator.respond(prompt, null) },
        title = { Text(copy.title) },
        text = { Text("${prompt.manifest.name}\n${copy.publisher}: $fingerprint\n\n${copy.body}\n\n${copy.warning}") },
        confirmButton = { TextButton(onClick = { coordinator.respond(prompt, language) }) { Text(copy.allow) } },
        dismissButton = { TextButton(onClick = { coordinator.respond(prompt, null) }) { Text(copy.decline) } })
}
