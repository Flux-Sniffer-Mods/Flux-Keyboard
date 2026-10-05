package it.palsoftware.pastiera.data.mappings

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomDeviceSymProfilesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun clear() {
        SettingsManager.getPreferences(context).edit()
            .remove(CustomDeviceSymProfiles.PREF_KEY).remove(CustomDeviceSymProfiles.PREF_CHOICE).commit()
    }

    @Test
    fun profilesAreSavedAndDeleted() {
        val created = CustomDeviceSymProfiles.create(context, "Mine", mapOf(KeyEvent.KEYCODE_Q to "!"))
        assertEquals(listOf(created), CustomDeviceSymProfiles.all(context))
        CustomDeviceSymProfiles.save(context, created.copy(name = "Renamed"))
        assertEquals("Renamed", CustomDeviceSymProfiles.get(context, created.id)?.name)
        CustomDeviceSymProfiles.delete(context, created.id)
        assertTrue(CustomDeviceSymProfiles.all(context).isEmpty())
    }

    @Test
    fun jsonRoundTripUsesTheBundledFormat() {
        val profile = CustomDeviceSymProfile("abc", "Mine", setOf("titan2"), mapOf(KeyEvent.KEYCODE_W to "1", KeyEvent.KEYCODE_GRAVE to "£"))
        val json = CustomDeviceSymProfiles.toJson(profile)
        assertEquals("1", json.getJSONObject("mappings").getString("KEYCODE_W"))
        assertEquals(profile, CustomDeviceSymProfiles.fromJson(json))
        val imported = CustomDeviceSymProfiles.fromJson(json, newId = true)!!
        assertNotEquals(profile.id, imported.id)
        assertEquals(profile.mappings, imported.mappings)
    }

    @Test
    fun clonesStartFromTheCuratedProfile() {
        val titan2 = CustomDeviceSymProfiles.bundledMappings(context.assets, "titan2")
        assertTrue(titan2.isNotEmpty())
        assertTrue(CustomDeviceSymProfiles.bundledMappings(context.assets, "nope").isEmpty())
    }

    @Test
    fun aMatchingProfileReplacesTheKeyboardsLayer() {
        val resolved = DeviceSymProfileResolver.resolve(context)
        val mine = CustomDeviceSymProfiles.create(context, "Mine", mapOf(KeyEvent.KEYCODE_Q to "Ω"))
        assertNotEquals("Ω", DeviceSymMappingRepository.load(context.assets, context)[KeyEvent.KEYCODE_Q])
        // Chosen by name (the Alt layer's choice)
        assertEquals("Ω", DeviceSymMappingRepository.load(context.assets, context, mine.profileRef)[KeyEvent.KEYCODE_Q])
        // Set to replace the keyboard in use
        CustomDeviceSymProfiles.save(context, mine.copy(matchProfiles = setOf(resolved)))
        assertEquals("Ω", DeviceSymMappingRepository.load(context.assets, context)[KeyEvent.KEYCODE_Q])
        // A curated profile asked for by name stays curated
        assertNotEquals("Ω", DeviceSymMappingRepository.load(context.assets, context, "titan2")[KeyEvent.KEYCODE_Q])
        assertNull(CustomDeviceSymProfiles.forRef(context, "titan2"))
    }

    @Test
    fun theLayerInUseCanBeAnyCuratedOrOwnProfile() {
        val q25 = CustomDeviceSymProfiles.bundledMappings(context.assets, "Q25")
        CustomDeviceSymProfiles.setChoice(context, "Q25")
        assertEquals(q25[KeyEvent.KEYCODE_Q], DeviceSymMappingRepository.load(context.assets, context)[KeyEvent.KEYCODE_Q])

        val mine = CustomDeviceSymProfiles.create(context, "Mine", mapOf(KeyEvent.KEYCODE_Q to "Ω"))
        CustomDeviceSymProfiles.setChoice(context, mine.profileRef)
        assertEquals("Ω", DeviceSymMappingRepository.load(context.assets, context)[KeyEvent.KEYCODE_Q])

        // Deleting it goes back to the keyboard's own layer
        CustomDeviceSymProfiles.delete(context, mine.id)
        assertEquals(CustomDeviceSymProfiles.AUTO, CustomDeviceSymProfiles.choice(context))
        assertNotEquals("Ω", DeviceSymMappingRepository.load(context.assets, context)[KeyEvent.KEYCODE_Q])
    }
}
