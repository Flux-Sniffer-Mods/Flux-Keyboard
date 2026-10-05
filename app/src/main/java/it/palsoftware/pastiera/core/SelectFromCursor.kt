package it.palsoftware.pastiera.core

/**
 * Selecting from the cursor (a key shortcut or quick launcher command): Nav Mode turns on and
 * every move (its arrows, Home and End, cursor swipes) extends the selection from where the
 * cursor was. Leaving Nav Mode ends it.
 */
object SelectFromCursor {
    @Volatile
    var active: Boolean = false
}
