package it.palsoftware.pastiera.gaming

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Xbox Cloud Gaming in Flux Keyboard's own player: xbox.com/play in a web page, full screen,
 * signed in like a browser (its cookies kept). The page is given a controller of its own (the
 * web's standard gamepad), played by the keyboard and trackpad in Flux Keyboard's layout, so
 * every cloud game plays with keys, not only those with touch controls.
 */
class XboxPlayActivity : Activity() {
    private lateinit var web: WebView

    /** The controller's state: 17 buttons (the web's standard order), then the sticks (left x y, right x y). */
    private val buttons = FloatArray(17)
    private val stickKeys = FloatArray(4)
    private val trackpad = FloatArray(4)
    private val held = mutableSetOf<Int>()

    /** Signing in: a plain browser page, keys typing, no controller. */
    private val signingIn by lazy { intent.getBooleanExtra(EXTRA_SIGN_IN, false) }

    /** A text field has the focus in the page: keys type there, not play. */
    @Volatile private var typing = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        // Drawn on the GPU in a layer of its own: scrolling and typing stay smooth
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setContentView(web)
        immersive()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Signing in: the page laid out for a phone (not a desktop page shrunk to fit)
            loadWithOverviewMode = !signingIn
            useWideViewPort = !signingIn
            // A phone's Chrome, not a web view: the site plays only in browsers it knows
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) = inject()
            override fun onPageFinished(view: WebView, url: String?) = inject()
        }
        // The page says when a text field has the focus (a search, a sign-in form)
        web.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun typing(on: Boolean) { this@XboxPlayActivity.typing = on }
        }, "FluxTyping")
        web.webChromeClient = object : WebChromeClient() {
            // The stream's audio and video: nothing asked of the phone beyond what playing needs
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }.toTypedArray())
            }
        }
        web.loadUrl(if (signingIn) SIGN_IN else intent.dataString?.takeIf { it.startsWith("https://www.xbox.com/") } ?: HOME)
        // The page may be rebuilt as it moves between screens: the controller kept in it
        // (not while signing in: the sign-in pages have no controller to keep)
        if (!signingIn) web.postDelayed(object : Runnable {
            override fun run() { inject(); web.postDelayed(this, 5_000) }
        }, 2_000)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        immersive()
        if (signingIn) return
        val screen = GameProfiles.all(this).firstOrNull { p -> p.launch?.contains(intent.dataString ?: "\u0000") == true }?.screen
            ?: ScreenMode.SIDEWAYS
        GameMode.attachPlayer(this, screen) { sticks ->
            sticks.copyInto(trackpad)
            runOnUiThread { push() }
        }
    }

    override fun onPause() {
        if (!signingIn) GameMode.detachPlayer(this)
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun immersive() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        web.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    /** The page's controller, if it hasn't one yet, and word of text fields taking the focus. */
    private fun inject() {
        web.evaluateJavascript(TYPING_SCRIPT, null)
        if (!signingIn) web.evaluateJavascript(PAD_SCRIPT, null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Signing in or typing in a field: keys type
        if (signingIn || typing) return super.dispatchKeyEvent(event)
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return super.dispatchKeyEvent(event)
        }
        val code = event.keyCode
        val control = FluxLayout.KEYS.firstOrNull { it.first == code }?.second
        // Back leaves the page's screen, then the player; other keys the page doesn't know reach it as they are
        if (control == null) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0) return true
        if (down) held += code else held -= code
        val index = BUTTON_INDEX[control]
        if (index != null) {
            buttons[index] = if (FluxLayout.KEYS.any { it.first in held && BUTTON_INDEX[it.second] == index }) 1f else 0f
        } else {
            fun on(c: FluxLayout.Control) = if (FluxLayout.KEYS.any { it.first in held && it.second == c }) 1f else 0f
            stickKeys[0] = on(FluxLayout.Control.LS_RIGHT) - on(FluxLayout.Control.LS_LEFT)
            stickKeys[1] = on(FluxLayout.Control.LS_DOWN) - on(FluxLayout.Control.LS_UP)
            stickKeys[2] = on(FluxLayout.Control.RS_RIGHT) - on(FluxLayout.Control.RS_LEFT)
            stickKeys[3] = on(FluxLayout.Control.RS_DOWN) - on(FluxLayout.Control.RS_UP)
        }
        push()
        return true
    }

    /** The controller's state, into the page. */
    private fun push() {
        // Keys held push a stick fully; else the trackpad's
        val axes = FloatArray(4) { if (stickKeys[it] != 0f) stickKeys[it] else trackpad[it] }
        web.evaluateJavascript(
            "window.__fluxPad&&__fluxPad.set([${buttons.joinToString(",")}],[${axes.joinToString(",")}])", null
        )
    }

    @Deprecated("Back: the page's own back first")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    companion object {
        const val HOME = "https://www.xbox.com/play"
        /** Xbox's own sign-in (a Microsoft account), back to cloud gaming after. */
        const val SIGN_IN = "https://www.xbox.com/en-US/auth/msa?action=logIn&returnUrl=https%3A%2F%2Fwww.xbox.com%2Fplay"
        private const val EXTRA_SIGN_IN = "sign_in"

        /** The player as a plain browser page, to sign in to Xbox. */
        fun signIn(context: android.content.Context): android.content.Intent =
            android.content.Intent(context, XboxPlayActivity::class.java).putExtra(EXTRA_SIGN_IN, true)

        /** Tells the player whenever a text field takes or loses the focus. */
        private const val TYPING_SCRIPT = """(function(){
  if (window.__fluxTyping || !window.FluxTyping) return;
  window.__fluxTyping = true;
  var check = function(){
    var e = document.activeElement;
    var on = !!e && (e.tagName === 'INPUT' || e.tagName === 'TEXTAREA' || e.isContentEditable);
    FluxTyping.typing(on);
  };
  document.addEventListener('focusin', check, true);
  document.addEventListener('focusout', function(){ setTimeout(check, 0); }, true);
  check();
})();"""

        /** Each control's button in the web's standard gamepad. */
        private val BUTTON_INDEX = mapOf(
            FluxLayout.Control.A to 0, FluxLayout.Control.B to 1, FluxLayout.Control.X to 2, FluxLayout.Control.Y to 3,
            FluxLayout.Control.L1 to 4, FluxLayout.Control.R1 to 5, FluxLayout.Control.L2 to 6, FluxLayout.Control.R2 to 7,
            FluxLayout.Control.SELECT to 8, FluxLayout.Control.START to 9, FluxLayout.Control.L3 to 10, FluxLayout.Control.R3 to 11,
            FluxLayout.Control.UP to 12, FluxLayout.Control.DOWN to 13, FluxLayout.Control.LEFT to 14, FluxLayout.Control.RIGHT to 15,
            FluxLayout.Control.HOME to 16
        )

        /**
         * A standard gamepad for the page: navigator.getGamepads() answers with it, and the page
         * is told it was plugged in. Its state comes from __fluxPad.set(buttons, axes).
         */
        private const val PAD_SCRIPT = """(function(){
  if (window.__fluxPad) return;
  var buttons = []; for (var i = 0; i < 17; i++) buttons.push({pressed:false, touched:false, value:0});
  var pad = {id:'Xbox Wireless Controller (STANDARD GAMEPAD Vendor: 045e Product: 0b13)', index:0, connected:true,
    mapping:'standard', timestamp:performance.now(), buttons:buttons, axes:[0,0,0,0], vibrationActuator:null, hapticActuators:[]};
  try { Object.defineProperty(Navigator.prototype, 'getGamepads', {configurable:true, value:function(){ return [pad, null, null, null]; }}); }
  catch (e) { navigator.getGamepads = function(){ return [pad, null, null, null]; }; }
  window.__fluxPad = {set:function(b, a){
    for (var i = 0; i < 17; i++) { var v = b[i] || 0; buttons[i].pressed = v > 0.1; buttons[i].touched = v > 0; buttons[i].value = v; }
    for (var j = 0; j < 4; j++) pad.axes[j] = a[j] || 0;
    pad.timestamp = performance.now();
  }};
  var plug = function(){ var e = new Event('gamepadconnected'); e.gamepad = pad; window.dispatchEvent(e); };
  plug(); setTimeout(plug, 1500);
})();"""
    }
}
