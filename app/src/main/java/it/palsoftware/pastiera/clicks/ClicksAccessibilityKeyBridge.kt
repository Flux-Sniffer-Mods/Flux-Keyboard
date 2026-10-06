package it.palsoftware.pastiera.clicks

import android.view.KeyEvent
import android.view.MotionEvent

/** Connects the optional system key filter to the currently active Pastiera input method. */
internal object ClicksAccessibilityKeyBridge {
    interface Target {
        fun dispatchClicksAccessibilityKeyEvent(event: KeyEvent): Boolean
        fun dispatchClicksDirectAction(action: ClicksButtonDirectAction): Boolean
        /** A trackpad swipe the accessibility service took before any app saw it. */
        fun dispatchCapturedTrackpadMotion(event: MotionEvent): Boolean
    }

    /**
     * Whether the trackpad's swipes are the keyboard's now (a text field is being typed in, and
     * the option is on): the accessibility service takes them before apps while it is.
     */
    @Volatile
    var trackpadCaptured: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            onTrackpadCaptureChanged?.invoke(value)
        }

    @Volatile
    var onTrackpadCaptureChanged: ((Boolean) -> Unit)? = null

    /** The per-app swipe lists changed: the service works out again whether to take swipes. */
    fun refreshTrackpadClaim() {
        onTrackpadCaptureChanged?.invoke(trackpadCaptured)
    }

    fun dispatch(event: MotionEvent): Boolean =
        target?.dispatchCapturedTrackpadMotion(event) == true

    @Volatile
    private var target: Target? = null

    fun register(target: Target) {
        this.target = target
    }

    fun unregister(target: Target) {
        if (this.target === target) this.target = null
    }

    fun dispatch(event: KeyEvent): Boolean =
        target?.dispatchClicksAccessibilityKeyEvent(event) == true

    fun dispatch(action: ClicksButtonDirectAction): Boolean =
        target?.dispatchClicksDirectAction(action) == true
}
