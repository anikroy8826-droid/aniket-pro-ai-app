package com.aniket.proai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private val SITE = "https://graceful-dieffenbachia-986a50.netlify.app"

    private lateinit var web: WebView
    private lateinit var wm: WindowManager
    private lateinit var pref: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: TextView? = null
    private var splash: View? = null
    private var settingsDialog: AlertDialog? = null
    private var capturing = false
    private var bubbleEnabled = false
    private var galleryDeleteEnabled = false
    private var inFront = true
    private var shotUris = mutableListOf<Uri>()
    private var fileCb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        pref = getSharedPreferences("app", MODE_PRIVATE)
        bubbleEnabled = pref.getBoolean("bubble_on", false)
        galleryDeleteEnabled = pref.getBoolean("gallery_delete", false)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun setBubble(on: Boolean) {
                pref.edit().putBoolean("bubble_on", on).apply()
                runOnUiThread {
                    bubbleEnabled = on
                    updateBubble()
                }
            }
            @JavascriptInterface
            fun setGalleryDelete(on: Boolean) {
                pref.edit().putBoolean("gallery_delete", on).apply()
                runOnUiThread { galleryDeleteEnabled = on }
            }
        }, "AndroidBridge")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                wv: WebView?, cb: ValueCallback<Array<Uri>>?, fp: FileChooserParams?
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "SS bachao"), 101)
                } catch (e: Exception) {
                    fileCb?.onReceiveValue(null)
                    fileCb = null
                    return false
                }
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                splash?.visibility = View.GONE
            }
        }

        val container = FrameLayout(this)
        container.addView(web, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))

        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000"))
                cornerRadius = dp(22).toFloat()
            }
            setOnClickListener { showSettings() }
        }
        container.addView(gear, FrameLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(14)
            topMargin = dp(14)
        })

        val lay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0d0d0d"))
        }
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(110))
        }
        val tv = TextView(this).apply {
            text = "Loading..."
            setTextColor(Color.parseColor("#e9d8a6"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        lay.addView(iv)
        lay.addView(tv)
        container.addView(lay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        splash = lay

        setContentView(container)
        web.loadUrl(SITE)
        askPerms()
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        handler.postDelayed({ showBubble() }, 1500)
    }

    private fun dp(v: Int): Int {
        return Math.round(v * resources.displayMetrics.density)
    }

    private fun showSettings() {
        settingsDialog?.dismiss()
        val cardW = Math.min(dp(320),
            (resources.displayMetrics.widthPixels * 0.85).toInt())
        val pad = dp(18)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1a1a1a"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#d4af37"))
            }
        }

        val title = TextView(this).apply {
            text = "⚙ Settings"
            setTextColor(Color.parseColor("#e9d8a6"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

        val hint1 = TextView(this).apply {
            text = "🎛 Floating Bubble"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(hint1)
        val sub1 = TextView(this).apply {
            text = "App er baire floating button dekhabe"
            setTextColor(Color.parseColor("#999999"))
            textSize = 12f
        }
        card.addView(sub1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        val sw1 = Switch(this).apply {
            isChecked = bubbleEnabled
            setOnCheckedChangeListener { _, on ->
                bubbleEnabled = on
                pref.edit().putBoolean("bubble_on", on).apply()
                updateBubble()
            }
        }
        card.addView(sw1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) })

        val hint2 = TextView(this).apply {
            text = "🗑 Gallery Auto-Delete"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(hint2)
        val sub2 = TextView(this).apply {
            text = "Import er por ss gallery theke muche felbe"
            setTextColor(Color.parseColor("#999999"))
            textSize = 12f
        }
        card.addView(sub2, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        val sw2 = Switch(this).apply {
            isChecked = galleryDeleteEnabled
            setOnCheckedChangeListener { _, on ->
                galleryDeleteEnabled = on
                pref.edit().putBoolean("gallery_delete", on).apply()
            }
        }
        card.addView(sw2, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val closeBtn = TextView(this).apply {
            text = "✓ Done"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1a1a1a"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#d4af37"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { settingsDialog?.dismiss() }
        }
        card.addView(closeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        val dialog = AlertDialog.Builder(this)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(cardW, WindowManager.LayoutParams.WRAP_CONTENT)
        settingsDialog = dialog
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        updateBubble()
    }

    override fun onPause() {
        super.onPause()
        inFront = false
        updateBubble()
    }

    private fun updateBubble() {
        val v = bubble ?: return
        val show = bubbleEnabled && (capturing || inFront)
        v.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun refreshBubble() {
        val v = bubble ?: return
        val hasGlyph = try { Paint().hasGlyph("ꫝ") } catch (e: Throwable) { false }
        v.text = if (hasGlyph) "ꫝ " + shotUris.size else "" + shotUris.size
        v.setTextColor(if (capturing)
            Color.parseColor("#FF8A80") else Color.parseColor("#E9D8A6"))
        if (!hasGlyph) {
            try {
                val d = getDrawable(R.drawable.app_logo)
                val s = dp(18)
                d?.setBounds(0, 0, s, s)
                v.setCompoundDrawables(d, null, null, null)
                v.compoundDrawablePadding = dp(3)
            } catch (e: Exception) { }
        }
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return
        val v = TextView(this)
        v.textSize = 16f
        v.setShadowLayer(6f, 0f, 0f, Color.BLACK)
        v.setBackgroundColor(Color.TRANSPARENT)
        v.setPadding(dp(6), dp(6), dp(6), dp(6))
        refreshBubble()
        v.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false
            private var lpFired = false
            private val lpRun = Runnable {
                lpFired = true
                stopCapture(true)
            }
            override fun onTouch(vv: View, e: MotionEvent): Boolean {
                val p = vv.layoutParams as WindowManager.LayoutParams
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = p.x
                        startY = p.y
                        moved = false
                        lpFired = false
                        handler.postDelayed(lpRun, 650)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (!moved && Math.abs(dx) + Math.abs(dy) > dp(12)) {
                            moved = true
                            handler.removeCallbacks(lpRun)
                        }
                        if (moved) {
                            p.x = startX - dx
                            p.y = startY - dy
                            wm.updateViewLayout(vv, p)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(lpRun)
                        if (!moved && !lpFired &&
                            e.actionMasked == MotionEvent.ACTION_UP) {
                            stopCapture(false)
                        }
                    }
                }
                return true
            }
        })
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.END
        p.x = 8
        p.y = 260
        wm.addView(v, p)
        bubble = v
        updateBubble()
    }

    private fun stopCapture(toHtf: Boolean) {
        if (!capturing) {
            capturing = true
            shotUris.clear()
        } else {
            capturing = false
            pushToWeb(toHtf)
        }
        refreshBubble()
        updateBubble()
    }

    private fun pushToWeb(htf: Boolean) {
        val urls = mutableListOf<String>()
        val imported = mutableListOf<Uri>()
        for (u in shotUris) {
            try {
                val ins = contentResolver.openInputStream(u) ?: continue
                val bmp0 = BitmapFactory.decodeStream(ins) ?: continue
                ins.close()
                var bmp = bmp0
                val sc = Math.min(1.0, 1568.0 / Math.max(bmp.width, bmp.height).toDouble())
                if (sc < 1.0) bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true)
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                urls.add("data:image/jpeg;base64," +
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                imported.add(u)
            } catch (e: Exception) { }
        }
        shotUris.clear()
        refreshBubble()
        if (urls.isEmpty()) return
        val json = urls.joinToString(",", "[", "]") { "\"$it\"" }
        val fn = if (htf) "__ssImportHTF" else "__ssImport"
        web.evaluateJavascript("if(window.$fn)window.$fn($json)", null)

        if (galleryDeleteEnabled && Build.VERSION.SDK_INT >= 30 && imported.isNotEmpty()) {
            try {
                var pend: android.app.PendingIntent? = null
                try {
                    val m = MediaStore::class.java.getMethod(
                        "createDeleteRequest",
                        android.content.Context::class.java,
                        java.util.Collection::class.java)
                    pend = m.invoke(null, this, imported) as android.app.PendingIntent
                } catch (e: Exception) {
                    val m2 = MediaStore::class.java.getMethod(
                        "createDeleteRequest",
                        android.content.ContentResolver::class.java,
                        java.util.Collection::class.java)
                    pend = m2.invoke(null, contentResolver, imported) as android.app.PendingIntent
                }
                pend?.let {
                    startIntentSenderForResult(it.intentSender, 102, null, 0, 0, 0)
                }
            } catch (e: Exception) { }
        }
    }

    private fun askPerms() {
        val l = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.READ_MEDIA_IMAGES)
        else l.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (l.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            requestPermissions(l.toTypedArray(), 1)
        if (!Settings.canDrawOverlays(this))
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")), 2)
    }

    private val observer = object : android.database.ContentObserver(handler) {
        override fun onChange(self: Boolean) {
            if (!capturing) return
            handler.postDelayed({ scanShots() }, 900)
        }
    }

    private fun scanShots() {
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED)
        val cur = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            MediaStore.Images.Media.DATE_ADDED + " DESC")
        cur?.use {
            while (it.moveToNext()) {
                val name = it.getString(1)?.lowercase() ?: continue
                if (!name.contains("screenshot")) continue
                val added = it.getLong(2)
                if (System.currentTimeMillis() / 1000 - added > 180) continue
                val u = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    it.getLong(0).toString())
                if (!shotUris.contains(u)) shotUris.add(u)
            }
        }
        refreshBubble()
    }

    override fun onActivityResult(rc: Int, rc2: Int, d: Intent?) {
        if (rc == 101) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rc2, d))
            fileCb = null
        }
        if (rc == 2) handler.postDelayed({ showBubble() }, 1000)
        super.onActivityResult(rc, rc2, d)
    }

    override fun onDestroy() {
        try { contentResolver.unregisterContentObserver(observer) } catch (e: Exception) { }
        bubble?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        settingsDialog?.dismiss()
        super.onDestroy()
  // build v5  }
}
