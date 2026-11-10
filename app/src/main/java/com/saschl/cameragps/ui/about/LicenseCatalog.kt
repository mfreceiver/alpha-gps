package com.saschl.cameragps.ui.about

import org.jetbrains.compose.resources.StringResource
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m9_licenses_purpose_app
import cameragps.sharednew.generated.resources.m9_licenses_purpose_haze
import cameragps.sharednew.generated.resources.m9_licenses_purpose_heif
import cameragps.sharednew.generated.resources.m9_licenses_purpose_icons
import cameragps.sharednew.generated.resources.m9_licenses_purpose_logging
import cameragps.sharednew.generated.resources.m9_licenses_purpose_timber

/** One third-party (or own) component listed on the licenses page. */
internal data class LicenseEntry(
    val id: String,
    val name: String,
    val licenseName: String,
    /** File under `assets/licenses/` holding the full license text (bundled, works offline). */
    val assetFile: String,
    val purpose: StringResource? = null,
)

internal object LicenseCatalog {
    const val GPL_ASSET = "GPL-3.0.txt"
    const val APACHE_ASSET = "Apache-2.0.txt"
    const val LIBHEIF_ASSET = "libheif-COPYING.txt"
    const val LIBDE265_ASSET = "libde265-COPYING.txt"

    /** Mirrors docs/legal-notes.md section 5 (third-party component list). */
    val entries: List<LicenseEntry> = listOf(
        LicenseEntry("app", "AlphaGPS", "GPL-3.0", GPL_ASSET, Res.string.m9_licenses_purpose_app),
        LicenseEntry("libheif", "libheif 1.23.5", "LGPL-3.0-or-later", LIBHEIF_ASSET, Res.string.m9_licenses_purpose_heif),
        LicenseEntry("libde265", "libde265 1.1.3", "LGPL-3.0-or-later", LIBDE265_ASSET, Res.string.m9_licenses_purpose_heif),
        LicenseEntry("kotlin", "Kotlin, kotlinx.serialization / coroutines / datetime", "Apache-2.0", APACHE_ASSET),
        LicenseEntry("compose", "Jetpack Compose, Compose Multiplatform", "Apache-2.0", APACHE_ASSET),
        LicenseEntry("androidx", "AndroidX (Core, Activity, Lifecycle, Navigation 3, Room, ExifInterface…)", "Apache-2.0", APACHE_ASSET),
        LicenseEntry("material", "Material Components for Android", "Apache-2.0", APACHE_ASSET),
        LicenseEntry("icons", "Material Icons / Material Symbols", "Apache-2.0", APACHE_ASSET, Res.string.m9_licenses_purpose_icons),
        LicenseEntry("haze", "Haze (dev.chrisbanes.haze)", "Apache-2.0", APACHE_ASSET, Res.string.m9_licenses_purpose_haze),
        LicenseEntry("timber", "Timber", "Apache-2.0", APACHE_ASSET, Res.string.m9_licenses_purpose_timber),
        LicenseEntry("accompanist", "Accompanist (Permissions)", "Apache-2.0", APACHE_ASSET),
        LicenseEntry("logging", "KmLogging (com.diamondedge:logging)", "Apache-2.0", APACHE_ASSET, Res.string.m9_licenses_purpose_logging),
    )

    fun find(id: String): LicenseEntry? = entries.firstOrNull { it.id == id }
}
