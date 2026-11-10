package com.saschl.cameragps.ui.about

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the bundled license texts shown by the About / Licenses pages. */
class LicenseCatalogTest {

    // Gradle runs app unit tests with the module directory (app/) as the working directory.
    private val appAssets = File("src/main/assets/licenses")
    private val heifAssets = File("../heif/src/main/assets/licenses")
    private val rootLicense = File("../LICENSE")

    private fun resolve(name: String): File? =
        listOf(appAssets, heifAssets).map { File(it, name) }.firstOrNull { it.isFile }

    @Test
    fun everyCatalogEntryHasABundledNonEmptyLicenseText() {
        for (entry in LicenseCatalog.entries) {
            val file = resolve(entry.assetFile)
            assertNotNull("missing license asset ${entry.assetFile} for ${entry.id}", file)
            assertTrue("empty license asset ${entry.assetFile}", file!!.length() > 1000)
        }
    }

    @Test
    fun entryIdsAreUnique() {
        val ids = LicenseCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun hazeIsListedAsApache() {
        val haze = LicenseCatalog.find("haze")
        assertNotNull(haze)
        assertEquals("Apache-2.0", haze!!.licenseName)
    }

    @Test
    fun bundledGplTextMatchesRepositoryLicense() {
        assertTrue("repo LICENSE not found from ${File(".").absolutePath}", rootLicense.isFile)
        assertEquals(
            rootLicense.readText().trim(),
            File(appAssets, LicenseCatalog.GPL_ASSET).readText().trim(),
        )
    }
}
