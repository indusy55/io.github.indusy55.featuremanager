package io.github.indusy55.featuremanager

import android.content.pm.PackageManager
import io.github.indusy55.featuremanager.core.ConfigContract
import io.github.indusy55.featuremanager.core.FeatureCatalog
import io.github.indusy55.featuremanager.data.BuiltinTemplates
import io.github.indusy55.featuremanager.data.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    @Test
    fun pcVariantTemplateForcesDesktopFeaturesAndRemovesTvFeatures() {
        val rules = BuiltinTemplates.VARIANT_PC.rules

        assertEquals(Decision.FORCE_TRUE, rules[PackageManager.FEATURE_PC])
        assertEquals(Decision.FORCE_TRUE, rules[FeatureCatalog.FEATURE_CHROME_ARC])
        assertEquals(Decision.FORCE_TRUE, rules[PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT])
        assertEquals(Decision.FORCE_FALSE, rules[PackageManager.FEATURE_TELEVISION])
        assertEquals(Decision.FORCE_FALSE, rules[PackageManager.FEATURE_LEANBACK])
        assertEquals(Decision.FORCE_FALSE, rules[PackageManager.FEATURE_TELEPHONY])
    }

    @Test
    fun tvVariantTemplateForcesLeanbackAndRemovesTouchscreen() {
        val rules = BuiltinTemplates.VARIANT_TV.rules

        assertEquals(Decision.FORCE_TRUE, rules[PackageManager.FEATURE_LEANBACK])
        assertEquals(Decision.FORCE_TRUE, rules[PackageManager.FEATURE_LEANBACK_ONLY])
        assertEquals(Decision.FORCE_TRUE, rules[PackageManager.FEATURE_TELEVISION])
        assertEquals(Decision.FORCE_FALSE, rules[PackageManager.FEATURE_TOUCHSCREEN])
        assertEquals(Decision.FORCE_FALSE, rules[PackageManager.FEATURE_PC])
        assertEquals(Decision.FORCE_FALSE, rules[FeatureCatalog.FEATURE_CHROME_ARC])
    }

    @Test
    fun builtinTemplatesDoNotContainConflictingDecisions() {
        for (template in BuiltinTemplates.ALL) {
            assertTrue("${template.id} has no rules", template.rules.isNotEmpty())
            assertTrue(
                "${template.id} contains DEFAULT rules",
                template.rules.values.all { it != Decision.DEFAULT }
            )
        }
    }

    @Test
    fun packageNameValidationRejectsMalformedArguments() {
        assertTrue(ConfigContract.isValidPackageName("com.example.app"))
        assertTrue(ConfigContract.isValidPackageName("org.chromium.arc.device_management"))
        assertFalse(ConfigContract.isValidPackageName(null))
        assertFalse(ConfigContract.isValidPackageName(""))
        assertFalse(ConfigContract.isValidPackageName("com..app"))
        assertFalse(ConfigContract.isValidPackageName("com example"))
        assertFalse(ConfigContract.isValidPackageName("a".repeat(300)))
    }
}
