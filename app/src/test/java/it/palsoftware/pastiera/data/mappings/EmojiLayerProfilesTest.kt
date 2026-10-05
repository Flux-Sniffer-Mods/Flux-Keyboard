package it.palsoftware.pastiera.data.mappings

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiLayerProfilesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun clear() {
        SettingsManager.getPreferences(context).edit()
            .remove(EmojiLayerProfiles.PREF_KEY).remove(EmojiLayerProfiles.PREF_ACTIVE)
            .remove(EmojiLayerProfiles.PREF_SWITCH_BY_APP).commit()
        SettingsManager.resetSymMappings(context)
    }

    @Test
    fun everyBuiltInFillsTheLayerWithRealDistinctEmoji() {
        // The emoji picker's full list; variation selectors (U+FE0F) don't count
        val selector = Char(0xFE0F).toString()
        val known = java.io.File("src/main/assets/common/emoji").listFiles().orEmpty()
            .filter { it.name != "minApi.txt" }
            .flatMap { file -> file.readText().split(Regex("\\s+")) }
            .map { it.replace(selector, "") }
            .filter { it.isNotEmpty() }
            .toSet()
        EmojiLayerProfiles.BUILT_IN.forEach { profile ->
            assertEquals(profile.id, EmojiLayerProfiles.KEYS.toSet(), profile.mappings.keys)
            assertEquals(profile.id, 26, profile.mappings.values.toSet().size)
            profile.mappings.values.forEach { emoji ->
                assertTrue("${profile.id}: $emoji", emoji.replace(selector, "") in known)
            }
        }
        assertEquals(EmojiLayerProfiles.BUILT_IN.size, EmojiLayerProfiles.BUILT_IN.map { it.id }.toSet().size)
    }

    @Test
    fun theEverydayProfileIsTheBundledLayer() {
        val bundled = KeyMappingLoader.loadSymKeyMappings(context.assets)
        // The bundled layer is Everyday around the default Recents, GIF and search keys (Q, P, A)
        val defaultButtons = setOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A)
        assertEquals(bundled, EmojiLayerProfiles.aroundButtons(EmojiLayerProfiles.BUILT_IN.first { it.id == "everyday" }.mappings, defaultButtons))
    }

    @Test
    fun applyingMakesItTheEmojiLayer() {
        val work = EmojiLayerProfiles.BUILT_IN.first { it.id == "work" }
        EmojiLayerProfiles.apply(context, work)
        assertEquals(EmojiLayerProfiles.layerMappings(context, work), SettingsManager.getSymMappings(context))
        assertEquals("work", EmojiLayerProfiles.activeId(context))
    }

    @Test
    fun yourProfilesAreSavedSharedAndDeleted() {
        val mine = EmojiLayerProfiles.createCustom(context, "Mine", mapOf(KeyEvent.KEYCODE_Q to "🦄", KeyEvent.KEYCODE_SPACE to "x"))
        assertEquals(mapOf(KeyEvent.KEYCODE_Q to "🦄"), mine.mappings)
        assertEquals(listOf(mine), EmojiLayerProfiles.custom(context))
        val copy = EmojiLayerProfiles.fromJson(EmojiLayerProfiles.toJson(mine), newId = true)!!
        assertEquals(mine.mappings, copy.mappings)
        assertTrue(copy.id != mine.id)
        EmojiLayerProfiles.apply(context, mine)
        EmojiLayerProfiles.deleteCustom(context, mine.id)
        assertTrue(EmojiLayerProfiles.custom(context).isEmpty())
        assertNull(EmojiLayerProfiles.activeId(context))
    }

    @Test
    fun switchingByAppFollowsTheAppsCategory() {
        assertEquals("chatting", EmojiLayerProfiles.forApp("com.whatsapp")?.id)
        assertEquals("social", EmojiLayerProfiles.forApp("com.instagram.android")?.id)
        assertEquals("work", EmojiLayerProfiles.forApp("com.google.android.gm")?.id)
        assertEquals("travel", EmojiLayerProfiles.forApp("com.google.android.apps.maps")?.id)
        assertNull(EmojiLayerProfiles.forApp("unknown.app"))
        assertNull(EmojiLayerProfiles.forApp(null))
    }
}
