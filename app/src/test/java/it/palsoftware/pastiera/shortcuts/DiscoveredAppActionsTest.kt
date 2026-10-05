package it.palsoftware.pastiera.shortcuts

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveredAppActionsTest {
    private fun action(id: String, label: String = id) = DiscoveredAction(id, label, "intent:#Intent;component=x/.$id;end")

    private val compose = action("compose_message", "New message")
    private val scan = action("scan", "Scan QR code")
    private val search = action("searchShortcut", "Search")
    private val camera = action("camera", "Camera")
    private val later = action("saved", "Saved")
    private val settings = action("settings", "")
    private val actions = DiscoveredAppActions(listOf(scan, compose, search, camera, later), settings)

    @Test
    fun numberedShortcutsFollowTheAppsOrder() {
        assertEquals(scan, actions.forStandard(StandardShortcut.AppAction1))
        assertEquals(camera, actions.forStandard(StandardShortcut.AppAction4))
        assertNull(DiscoveredAppActions(listOf(scan)).forStandard(StandardShortcut.AppAction2))
    }

    @Test
    fun newAndSearchComeFromWhatTheShortcutSays() {
        assertEquals(compose, actions.forStandard(StandardShortcut.New))
        assertEquals(search, actions.forStandard(StandardShortcut.Search))
        assertNull(DiscoveredAppActions(listOf(scan, camera)).forStandard(StandardShortcut.New))
        // "newsfeed" is not "new"
        assertNull(DiscoveredAppActions(listOf(action("newsfeed", "Newsfeed"))).forStandard(StandardShortcut.New))
    }

    @Test
    fun wordsSplitIdsAndLabels() {
        assertEquals(setOf("new", "post"), DiscoveredAppActions.words("newPost"))
        assertEquals(setOf("compose", "message"), DiscoveredAppActions.words("compose_message"))
    }

    @Test
    fun discoveredActionsComeLast() {
        val config = AppShortcutConfig()
        val ctrlAlt1 = KeyCombo(KeyEvent.KEYCODE_1, ctrl = true, alt = true)
        val resolved = AppShortcutRemapper.resolve(config, "some.app", ctrlAlt1, inTextField = false) { actions }
        assertEquals(ShortcutAction.OpenDiscovered(scan), resolved)
        val settingsCombo = KeyCombo.ctrl(KeyEvent.KEYCODE_COMMA)
        assertEquals(
            ShortcutAction.OpenDiscovered(settings),
            AppShortcutRemapper.resolve(config, "some.app", settingsCombo, inTextField = true) { actions }
        )
        // Gmail documents Ctrl+N, so its own key wins over a discovered "compose"
        val gmailNew = AppShortcutRemapper.resolve(config, "com.google.android.gm", KeyCombo.ctrl(KeyEvent.KEYCODE_N), false) { actions }
        assertEquals(ShortcutAction.SendKeys::class, gmailNew!!::class)
    }

    @Test
    fun suggestionsOffOrYourChoiceTurnsThemOff() {
        val ctrlAlt1 = KeyCombo(KeyEvent.KEYCODE_1, ctrl = true, alt = true)
        assertNull(AppShortcutRemapper.resolve(AppShortcutConfig(suggestionsEnabled = false), "some.app", ctrlAlt1, false) { actions })
        val dontRemap = AppShortcutConfig(apps = mapOf("some.app" to AppShortcutAppSettings(overrides = mapOf(StandardShortcut.AppAction1 to null))))
        assertNull(AppShortcutRemapper.resolve(dontRemap, "some.app", ctrlAlt1, false) { actions })
    }
}
