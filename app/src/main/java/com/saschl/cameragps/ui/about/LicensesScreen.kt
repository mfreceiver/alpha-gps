package com.saschl.cameragps.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m9_about_licenses_entry
import cameragps.sharednew.generated.resources.m9_licenses_intro
import cameragps.sharednew.generated.resources.m9_licenses_load_failed
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold
import com.sasch.cameragps.sharednew.ui.components.verticalScrollbar
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsColumn
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsNavRow
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** List of components with their licenses; tapping an item opens its bundled full license text. */
@Composable
fun LicensesScreen(onBackClick: () -> Unit, onOpenLicense: (String) -> Unit) {
    val scrollState = rememberScrollState()
    GlassPageScaffold(
        title = stringResource(Res.string.m9_about_licenses_entry),
        onBackClick = onBackClick,
        scrolled = scrollState.isScrolledUnderTopBar,
    ) { padding ->
        SharedSettingsColumn(Modifier.fillMaxSize().padding(padding), state = scrollState) {
            BodyText(stringResource(Res.string.m9_licenses_intro))
            LicenseCatalog.entries.forEach { entry ->
                val purpose = entry.purpose?.let { stringResource(it) }
                SharedSettingsNavRow(
                    title = entry.name,
                    description = listOfNotNull(entry.licenseName, purpose).joinToString(" · "),
                    onClick = { onOpenLicense(entry.id) },
                )
            }
        }
    }
}

/** Full license text of one component, read from the bundled assets (no network). */
@Composable
fun LicenseTextScreen(licenseId: String?, gplText: Boolean = false, onBackClick: () -> Unit) {
    val entry = if (gplText) LicenseCatalog.find("app") else licenseId?.let(LicenseCatalog::find)
    val context = LocalContext.current
    val text by produceState<String?>(initialValue = null, entry) {
        value = entry?.let { e ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("licenses/${e.assetFile}").bufferedReader().use { it.readText() }
                }.getOrNull().orEmpty()
            }
        }
    }
    val scrollState = rememberScrollState()
    GlassPageScaffold(
        title = entry?.let { "${it.name} · ${it.licenseName}" } ?: stringResource(Res.string.m9_about_licenses_entry),
        onBackClick = onBackClick,
        scrolled = scrollState.isScrolledUnderTopBar,
    ) { padding ->
        GlassSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            SelectionContainer(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScrollbar(scrollState.scrollIndicatorState)
                        .verticalScroll(scrollState)
                ) {
                    val shown = text
                    Text(
                        text = when {
                            shown == null -> ""
                            shown.isEmpty() -> stringResource(Res.string.m9_licenses_load_failed)
                            else -> shown
                        },
                        style = AlphaType.Mono,
                        color = AlphaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}
