package com.yomira.app.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.yomira.core.ui.theme.CardSurface
import com.yomira.core.ui.theme.TextSecondary
import com.yomira.domain.extension.ExtensionLogo

/** Decorative: the adjacent source/repository name already supplies the accessible label. */
@Composable
internal fun ExtensionLogoImage(name: String, iconUrl: String?, repositoryUrl: String?) {
    val urls = remember(iconUrl, repositoryUrl) { ExtensionLogo.candidates(iconUrl, repositoryUrl) }
    var attempt by remember(urls) { mutableIntStateOf(0) }
    val url = urls.getOrNull(attempt)
    var loaded by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    val request = remember(context, url) {
        url?.let {
            ImageRequest.Builder(context).data(it)
                .diskCacheKey(ExtensionLogo.cacheKey(it)).memoryCacheKey(ExtensionLogo.cacheKey(it))
                .diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED)
                .decoderFactory(ExtensionIcoDecoder.Factory)
                .size(96).crossfade(false).build()
        }
    }
    Surface(modifier = Modifier.size(44.dp).clearAndSetSemantics {}, shape = CircleShape, color = CardSurface) {
        Box(contentAlignment = Alignment.Center) {
            if (!loaded) Text(ExtensionLogo.initials(name), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            if (request != null) AsyncImage(
                model = request, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(44.dp).padding(8.dp),
                onError = { attempt++ },
                onSuccess = { loaded = true },
            )
        }
    }
}
