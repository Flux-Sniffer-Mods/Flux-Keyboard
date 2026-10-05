package it.palsoftware.pastiera.shortcuts

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShortcutRemapperTest {
    private val gmail = "com.google.android.gm"
    private val chrome = "com.android.chrome"
    private val termux = "com.termux"
    private val defaults = AppShortcutConfig()

    private fun resolve(
        packageName: String?,
        pressed: KeyCombo,
        inTextField: Boolean = false,
        config: AppShortcutConfig = defaults
    ) = (AppShortcutRemapper.resolve(config, packageName, pressed, inTextField) as? ShortcutAction.SendKeys)?.combo

    private fun action(
        packageName: String?,
        pressed: KeyCombo,
        inTextField: Boolean = false,
        config: AppShortcutConfig = defaults
    ) = AppShortcutRemapper.resolve(config, packageName, pressed, inTextField)

    @Test
    fun standardComboBecomesTheAppsOwnShortcut() {
        assertEquals(KeyCombo.key(KeyEvent.KEYCODE_C), resolve(gmail, StandardShortcut.New.combo))
        assertEquals(KeyCombo.ctrl(KeyEvent.KEYCODE_U), resolve(gmail, StandardShortcut.Refresh.combo))
        assertEquals(KeyCombo.ctrl(KeyEvent.KEYCODE_T), resolve(chrome, StandardShortcut.New.combo))
        assertEquals(KeyCombo.ctrl(KeyEvent.KEYCODE_TAB), resolve(chrome, StandardShortcut.Next.combo))
    }

    @Test
    fun theAppsOwnShortcutIsLeftAlone() {
        assertNull(resolve(chrome, StandardShortcut.Search.combo))
        assertNull(resolve(gmail, StandardShortcut.Send.combo))
    }

    @Test
    fun unknownAppsAndCombosPassThrough() {
        assertNull(action("com.example.unknown", StandardShortcut.New.combo))
        assertNull(action(null, StandardShortcut.New.combo))
        assertNull(action(gmail, KeyCombo.ctrl(KeyEvent.KEYCODE_Q)))
    }

    @Test
    fun typedCharactersAndListActionsAreNotSentWhileTyping() {
        // Gmail's compose is C: it would type a "c" into the message
        assertNull(action(gmail, StandardShortcut.New.combo, inTextField = true))
        // Mark unread belongs to the list, whatever it is sent as
        assertNull(resolve("com.microsoft.office.outlook", StandardShortcut.MarkUnread.combo, inTextField = true))
        // A bare arrow would move the cursor
        assertNull(resolve("com.google.android.apps.dynamite", StandardShortcut.Next.combo, inTextField = true))
        // Not a character: Google Chat sends with Enter
        assertEquals(
            KeyCombo.key(KeyEvent.KEYCODE_ENTER),
            resolve("com.google.android.apps.dynamite", StandardShortcut.Send.combo, inTextField = true)
        )
    }

    @Test
    fun terminalKeepsCtrlLettersForTheShell() {
        listOf(StandardShortcut.New, StandardShortcut.Search, StandardShortcut.Refresh, StandardShortcut.Close)
            .forEach { assertNull(it.name, action(termux, it.combo, inTextField = true)) }
        assertEquals(
            KeyCombo(KeyEvent.KEYCODE_DPAD_DOWN, ctrl = true, alt = true),
            resolve(termux, StandardShortcut.Next.combo, inTextField = true)
        )
    }

    @Test
    fun switchesAndYourOwnShortcutsWin() {
        assertNull(action(gmail, StandardShortcut.New.combo, config = AppShortcutConfig(enabled = false)))
        val gmailOff = AppShortcutConfig(apps = mapOf(gmail to AppShortcutAppSettings(enabled = false)))
        assertNull(action(gmail, StandardShortcut.New.combo, config = gmailOff))

        val mine = KeyCombo.ctrl(KeyEvent.KEYCODE_N, shift = true)
        val changed = AppShortcutConfig(
            apps = mapOf(
                gmail to AppShortcutAppSettings(
                    overrides = mapOf(StandardShortcut.New to mine, StandardShortcut.Archive to null)
                )
            )
        )
        assertEquals(mine, resolve(gmail, StandardShortcut.New.combo, config = changed))
        assertNull(action(gmail, StandardShortcut.Archive.combo, config = changed))
        // Untouched shortcuts keep the preset
        assertEquals(KeyCombo.key(KeyEvent.KEYCODE_R), resolve(gmail, StandardShortcut.Reply.combo, config = changed))
    }

    @Test
    fun combosSurviveStorage() {
        val combos = listOf(
            KeyCombo.key(KeyEvent.KEYCODE_SLASH, shift = true),
            KeyCombo(KeyEvent.KEYCODE_DPAD_UP, ctrl = true, alt = true),
            KeyCombo(KeyEvent.KEYCODE_A, ctrl = true, alt = true, shift = true, meta = true)
        )
        combos.forEach { assertEquals(it, KeyCombo.parse(it.serialize())) }
        assertNull(KeyCombo.parse("hyper:29"))
        assertNull(KeyCombo.parse("ctrl:"))
    }

    @Test
    fun appsWithoutDocumentedShortcutsGetSuggestions() {
        val instagram = "com.instagram.android"
        val newPost = action(instagram, StandardShortcut.New.combo) as ShortcutAction.OpenInApp
        assertEquals(AppIntent.ACTION_SEND, newPost.intents.first().action)
        val search = action(instagram, StandardShortcut.Search.combo) as ShortcutAction.OpenInApp
        assertTrue(search.intents.any { it.data == "https://www.instagram.com/explore/" })
        // Suggestions work while typing too: they open a screen, they don't type
        assertTrue(action(instagram, StandardShortcut.Search.combo, inTextField = true) is ShortcutAction.OpenInApp)
        // Only New and Search are suggested
        assertNull(action(instagram, StandardShortcut.Refresh.combo))
    }

    @Test
    fun documentedShortcutsAndYourChoicesBeatSuggestions() {
        // Gmail documents C for New: never a suggestion instead
        assertEquals(KeyCombo.key(KeyEvent.KEYCODE_C), resolve(gmail, StandardShortcut.New.combo))
        val instagram = "com.instagram.android"
        val off = AppShortcutConfig(suggestionsEnabled = false)
        assertNull(action(instagram, StandardShortcut.New.combo, config = off))
        val dontRemap = AppShortcutConfig(
            apps = mapOf(instagram to AppShortcutAppSettings(overrides = mapOf(StandardShortcut.New to null)))
        )
        assertNull(action(instagram, StandardShortcut.New.combo, config = dontRemap))
    }

    @Test
    fun theCatalogueCoversTheCategories() {
        val byCategory = AppShortcutPresets.all.groupBy { it.category }
        // Play's top 100 Social plus the hand-picked social media apps
        val social = byCategory[AppCategory.Social].orEmpty().map { it.packageName }
        assertTrue("social ${social.size}", social.size >= 100 + HandPickedSocialApps.added.size)
        assertTrue(social.containsAll(listOf("com.twitter.android", "com.pinterest", "xyz.blueskyweb.app")))
        listOf(AppCategory.Communication, AppCategory.Shopping, AppCategory.Music).forEach {
            assertTrue("$it", (byCategory[it]?.size ?: 0) >= 10)
        }
        // X and Quora moved from News to Social
        assertTrue((byCategory[AppCategory.News]?.size ?: 0) >= 8)
        val keyboards = setOf("com.google.android.inputmethod.latin", "com.touchtype.swiftkey", "com.samsung.android.honeyboard")
        assertTrue(AppShortcutPresets.all.none { it.packageName in keyboards })
    }

    @Test
    fun presetsAreConsistent() {
        val packages = AppShortcutPresets.all.map { it.packageName }
        assertEquals(packages.size, packages.toSet().size)
        val standards = StandardShortcut.entries.map { it.combo }
        assertEquals("standard combos must differ", standards.size, standards.toSet().size)
        StandardShortcut.entries.forEach {
            assertTrue("${it.name} must hold Ctrl or Alt", it.combo.ctrl || it.combo.alt)
        }
        AppShortcutPresets.all.forEach { preset ->
            if (preset.source != null) {
                assertTrue(preset.appName, preset.source.startsWith("https://"))
                assertTrue(preset.appName, preset.shortcuts.isNotEmpty())
            } else {
                assertTrue(preset.appName, preset.shortcuts.isEmpty() && preset.suggested.isNotEmpty())
            }
        }
    }
}
