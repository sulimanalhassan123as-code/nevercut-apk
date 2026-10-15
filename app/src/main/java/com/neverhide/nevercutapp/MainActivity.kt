package com.neverhide.nevercutapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : android.app.Activity() {
    private lateinit var web: WebView
    private val HOME = "https://nevercut-studio.vercel.app/"
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER = 101
    private val PERMS = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestPerms()
        setupWeb()
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(HOME)
    }

    private fun requestPerms() {
        val wanted = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.READ_MEDIA_VIDEO)
            wanted.add(Manifest.permission.READ_MEDIA_IMAGES)
            wanted.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(wanted.toTypedArray(), PERMS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE // always fresh — avoids the app getting stuck on old builds
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        WebView.setWebContentsDebuggingEnabled(false)

        web.webChromeClient = object : WebChromeClient() {
            // let the site use the camera & mic (Camera Duet, voice-over)
            override fun onPermissionRequest(req: PermissionRequest) {
                runOnUiThread { req.grant(req.resources) }
            }
            // let the site import videos/photos from the phone
            override fun onShowFileChooser(
                wv: WebView, cb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = cb
                val intent = params.createIntent().apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try { startActivityForResult(Intent.createChooser(intent, "Pick a video or photo"), FILE_CHOOSER) }
                catch (e: android.content.ActivityNotFoundException) { filePathCallback = null; return false }
                return true
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) showOffline()
            }
            override fun onPageFinished(view: WebView, url: String) {
                web.visibility = View.VISIBLE
            }
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                return if (u.host != null && u.host!!.endsWith("vercel.app")) false
                else { startActivity(Intent(Intent.ACTION_VIEW, u)); true }
            }
        }

        // saving exported videos goes to Downloads via DownloadManager
        web.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val rq = DownloadManager.Request(Uri.parse(url))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                    .setAllowedOverMetered(true)
                    .setTitle(name)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(rq)
                toast("Saving to Downloads: $name")
            } catch (e: Exception) { toast("Could not save the file") }
        }
    }

    private fun showOffline() {
        web.loadUrl("file:///android_asset/offline.html")
    }

    fun retry(view: View) { web.loadUrl(HOME) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER) {
            val cb = filePathCallback ?: return
            filePathCallback = null
            val uris = mutableListOf<Uri>()
            val clip = data?.clipData
            if (clip != null) for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            else data?.data?.let { uris.add(it) }
            cb.onReceiveValue(if (resultCode == RESULT_OK && uris.isNotEmpty()) uris.toTypedArray() else null)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out) }

    private fun toast(s: String) = runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}
