package com.sportxp.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray

/**
 * L'interface de SportXP est la page web validée dans la simulation (assets/index.html),
 * affichée en plein écran. Ce fichier ajoute ce qu'une page web ne sait pas faire seule :
 * notifications à l'heure prévue (même appli fermée), vibrations, bouton retour, panneau de démo.
 */
class MainActivity : ComponentActivity() {
    lateinit var web: WebView
    private var insetTop = 0
    private var insetBottom = 0

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#EEF1F6"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = true
        web.isVerticalScrollBarEnabled = false
        web.addJavascriptInterface(Bridge(this), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("file:")) return false
                // liens externes : on les ouvre dans le panneau, sans quitter l'appli
                DemoDialog.show(this@MainActivity, url, "MuscleWiki")
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pushInsets()
            }
        }
        setContentView(web)

        ViewCompat.setOnApplyWindowInsetsListener(web) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            insetTop = bars.top
            insetBottom = bars.bottom
            pushInsets()
            insets
        }

        web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.onBack && window.onBack()) ? 1 : 0") { r ->
                    if (r != "1") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Transmet à la page la hauteur de la barre d'état et de la barre de navigation (en pixels CSS). */
    fun pushInsets() {
        if (!::web.isInitialized) return
        val d = resources.displayMetrics.density
        val top = (insetTop / d).toInt()
        val bottom = (insetBottom / d).toInt()
        web.evaluateJavascript(
            "document.documentElement.style.setProperty('--sat','${top}px');" +
                "document.documentElement.style.setProperty('--sab','${bottom}px');",
            null
        )
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.onAppResume && window.onAppResume()", null)
    }
}

/** Fonctions appelées depuis la page (window.Android.xxx). Elles tournent hors du fil principal. */
class Bridge(private val a: MainActivity) {

    @JavascriptInterface
    fun schedule(json: String) {
        Alarms.replaceAll(a.applicationContext, json)
    }

    @JavascriptInterface
    fun notify(title: String, text: String) {
        Notifier.show(a.applicationContext, title, text)
    }

    @JavascriptInterface
    fun vibrate(json: String) {
        try {
            val v = a.getSystemService(Vibrator::class.java) ?: return
            val t = json.trim()
            if (t.startsWith("[")) {
                val arr = JSONArray(t)
                val pattern = LongArray(arr.length() + 1)
                pattern[0] = 0
                for (i in 0 until arr.length()) pattern[i + 1] = arr.getLong(i)
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                v.vibrate(VibrationEffect.createOneShot(t.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // vibration indisponible : on ignore
        }
    }

    @JavascriptInterface
    fun openDemo(url: String, title: String) {
        a.runOnUiThread { DemoDialog.show(a, url, title) }
    }

    @JavascriptInterface
    fun setLightStatus(light: Boolean) {
        a.runOnUiThread {
            WindowInsetsControllerCompat(a.window, a.window.decorView).isAppearanceLightStatusBars = light
        }
    }

    @JavascriptInterface
    fun canExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return a.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    @JavascriptInterface
    fun requestExact() {
        a.runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tryStart(a, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${a.packageName}")))
            }
        }
    }

    @JavascriptInterface
    fun openAppSettings() {
        a.runOnUiThread {
            tryStart(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
        }
    }

    @JavascriptInterface
    fun openAutostart() {
        a.runOnUiThread {
            val candidates = listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            )
            for (c in candidates) {
                if (tryStart(a, Intent().setComponent(c))) return@runOnUiThread
            }
            tryStart(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
        }
    }

    private fun tryStart(c: Context, i: Intent): Boolean = try {
        c.startActivity(i); true
    } catch (e: Exception) {
        false
    }
}

/** Panneau qui affiche la démo MuscleWiki par-dessus l'appli. */
object DemoDialog {
    @SuppressLint("SetJavaScriptEnabled")
    fun show(a: MainActivity, url: String, title: String) {
        val d = Dialog(a)
        val density = a.resources.displayMetrics.density
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
        }
        val t = TextView(a).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor("#17233F"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val close = Button(a).apply { text = "Fermer" }
        head.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(close)
        val src = TextView(a).apply {
            text = "Vidéo : MuscleWiki"
            textSize = 12f
            setTextColor(Color.parseColor("#6B768C"))
            setPadding((16 * density).toInt(), 0, 0, (6 * density).toInt())
        }
        val wv = WebView(a).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()   // les liens restent dans le panneau
            loadUrl(url)
        }
        root.addView(head)
        root.addView(src)
        root.addView(wv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        d.setContentView(root)
        close.setOnClickListener { d.dismiss() }
        d.setOnDismissListener { wv.destroy() }
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (a.resources.displayMetrics.heightPixels * 0.88).toInt())
        }
        d.show()
    }
}
