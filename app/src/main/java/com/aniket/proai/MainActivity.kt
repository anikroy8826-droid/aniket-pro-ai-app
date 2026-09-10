package com.aniket.proai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private val SITE = "https://dazzling-snickerdoodle-137764.netlify.app"

    private lateinit var web: WebView
    private lateinit var wm: WindowManager
    private lateinit var pref: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: TextView? = null
    private var splash: View? = null
    private var capturing = false
    private var bubbleEnabled = false
    private var inFront = true
    private var shotUris = mutableListOf<Uri>()
    private var fileCb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        pref = getSharedPreferences("app", MODE_PRIVATE)
        bubbleEnabled = pref.getBoolean("bubble_on", false)
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
        setContentView(web)

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
            setTextColor(Color.parseColor("#d9c08a"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        lay.addView(iv)
        lay.addView(tv)
        addContentView(lay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        splash = lay

        web.loadUrl(SITE)
        askPerms()
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        handler.postDelayed({ showBubble() }, 1500)
    }

    private fun dp(v: Int): Int {
        return Math.round(v * resources.displayMetrics.density)
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
        v.text = if (Paint().hasGlyph("ꫝ"))
            "ꫝ " + shotUris.size else " " + shotUris.size
        v.setTextColor(if (capturing)
            Color.parseColor("#FF5252") else Color.parseColor("#E9D8A6"))
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return
        val v = TextView(this)
        v.textSize = 16f
        v.setShadowLayer(5f, 0f, 0f, Color.BLACK)
        v.setBackgroundColor(Color.TRANSPARENT)
        v.setPadding(dp(6), dp(6), dp(6), dp(6))
        if (!Paint().hasGlyph("ꫝ")) {
            val d = getDrawable(R.drawable.app_logo)
            val s = dp(20)
            d?.setBounds(0, 0, s, s)
            v.setCompoundDrawables(d, null, null, null)
            v.compoundDrawablePadding = dp(3)
        }
        v.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false
            private var lpFired = false
            private val lpRun = Runnable { lpFired = true; stopCapture(true) }
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
        refreshBubble()
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
            } catch (e: Exception) { }
        }
        shotUris.clear()
        refreshBubble()
        if (urls.isEmpty()) return
        val json = urls.joinToString(",", "[", "]") { "\"$it\"" }
        val fn = if (htf) "__ssImportHTF" else "__ssImport"
        web.evaluateJavascript("if(window.$fn)window.$fn($json)", null)
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
        super.onDestroy()
    }
}
