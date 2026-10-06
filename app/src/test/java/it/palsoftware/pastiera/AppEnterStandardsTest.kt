package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEnterStandardsTest {
    private val sendPreset = SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE

    @Test
    fun socialAppSendsInMessageBoxes() {
        assertEquals(EnterStandard.Chat, AppEnterStandards.standardFor("com.facebook.katana"))
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
            AppEnterStandards.behaviorFor("com.facebook.katana", sendPreset, fieldSends = true)
        )
    }

    @Test
    fun chatAppFollowsThePresetInEveryField() {
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
            AppEnterStandards.behaviorFor("com.facebook.katana", sendPreset, fieldSends = false)
        )
    }

    @Test
    fun chatAppFollowsThePreset() {
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND,
            AppEnterStandards.behaviorFor(
                "com.Slack",
                SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND,
                fieldSends = true
            )
        )
        assertEquals(SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION, AppEnterStandards.sendStrategyFor("com.Slack"))
    }

    @Test
    fun emailUsesCtrlEnterEverywhere() {
        assertEquals(EnterStandard.Email, AppEnterStandards.standardFor("com.google.android.gm"))
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND,
            AppEnterStandards.behaviorFor("com.google.android.gm", sendPreset, fieldSends = false)
        )
        assertEquals(
            SettingsManager.ENTER_SEND_STRATEGY_CTRL_ENTER,
            AppEnterStandards.sendStrategyFor("com.microsoft.office.outlook")
        )
    }

    @Test
    fun otherAppsFollowThePresetToo() {
        listOf("com.android.chrome", "com.google.android.contacts", "com.spotify.music", "unknown.app", null)
            .forEach { pkg ->
                assertEquals(EnterStandard.AppDefault, AppEnterStandards.standardFor(pkg))
                assertEquals(
                    SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
                    AppEnterStandards.behaviorFor(pkg, sendPreset, fieldSends = true)
                )
                assertEquals(SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION, AppEnterStandards.sendStrategyFor(pkg))
            }
    }

    @Test
    fun noteAppsKeepEnterForANewLine() {
        listOf("com.google.android.keep", "md.obsidian", "notion.id").forEach { pkg ->
            assertEquals(EnterStandard.Notes, AppEnterStandards.standardFor(pkg))
            assertEquals(
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND,
                AppEnterStandards.behaviorFor(pkg, sendPreset, fieldSends = false)
            )
        }
    }

    @Test
    fun shiftSendsPresetGivesEnterNewLineShiftSends() {
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND,
            AppEnterStandards.behaviorFor(
                "unknown.app", SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_SHIFT_SEND, fieldSends = false
            )
        )
    }

    @Test
    fun standardAppsListsOnlyChatAndEmail() {
        val apps = AppEnterStandards.standardApps()
        assertTrue(apps.size > 50)
        assertTrue(apps.none { it.second == EnterStandard.AppDefault })
        assertTrue(apps.any { it.first.packageName == "com.google.android.gm" && it.second == EnterStandard.Email })
    }
}
