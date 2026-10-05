package it.palsoftware.pastiera.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxScriptsTest {
    @Test
    fun scriptsAndTasksAreListedWithoutIconsOrHiddenFiles() {
        val listing = "./backup.sh\n./tasks/sync.sh\n./icons/backup.sh.png\n./.hidden\n./tools/.cache/x\n\n./tasks/sync.sh\n"
        assertEquals(listOf("backup.sh", "tasks/sync.sh"), TermuxScripts.parse(listing))
        assertEquals("sync", TermuxScripts.label("tasks/sync.sh"))
    }
}
