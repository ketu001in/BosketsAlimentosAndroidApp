package com.bosketsalimentos.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var offlineView: View

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var cameraFile: File? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = fileCallback ?: return@registerForActivityResult
            fileCallback = null
            var uris: Array<Uri>? = null
            if (result.resultCode == RESULT_OK) {
                val picked = result.data?.data
                uris = when {
                    picked != null -> arrayOf(picked)
                    cameraFile?.let { it.exists() && it.length() > 0 } == true ->
                        cameraUri?.let { arrayOf(it) }
                    else -> null
                }
            }
            cb.onReceiveValue(uris)
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress)
        offlineView = findViewById(R.id.offline)

        configureWebView()

        findViewById<View>(R.id.btn_retry).setOnClickListener { retry() }
        findViewById<View>(R.id.btn_server).setOnClickListener { promptServerUrl() }

        swipe.setColorSchemeResources(R.color.brand_teal, R.color.brand_coral)
        swipe.setOnRefreshListener {
            if (offlineView.visibility == View.VISIBLE) retry() else webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else if (ServerConfig.isConfigured(this)) {
            loadStartPage()
        } else {
            promptServerUrl()
        }
    }

    private fun startPath(): String =
        intent?.getStringExtra(EXTRA_OPEN_PATH) ?: ""

    private fun loadStartPage() {
        showWeb()
        webView.loadUrl(ServerConfig.url(this) + "/" + startPath())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val path = intent?.getStringExtra(EXTRA_OPEN_PATH)
        if (path != null && ServerConfig.isConfigured(this)) {
            webView.loadUrl(ServerConfig.url(this) + "/" + path)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = "$userAgentString BosketsApp/${BuildConfig.VERSION_NAME}"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val target = request.url
                val site = Uri.parse(ServerConfig.url(this@MainActivity))
                val scheme = target.scheme ?: ""
                val sameHost = (scheme == "http" || scheme == "https") &&
                    target.host.equals(site.host, ignoreCase = true)
                // The CMS / SuperUser portal must always open in the system browser,
                // never inside the app — even though it lives on the same domain.
                val isCmsPortal = (target.path ?: "").contains("/CMS_Portal", ignoreCase = true)
                // Keep our own pages in the app; the CMS portal + external links go to the system.
                return if (sameHost && !isCmsPortal) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, target))
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@MainActivity, target.toString(), Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) showOffline()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                progress.progress = newProgress
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                openFileChooser()
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType)
                    )
                }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    private fun openFileChooser() {
        val content = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        cameraFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", cameraFile!!
        )
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
        val chooser = Intent.createChooser(content, getString(R.string.choose_file))
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        try {
            fileChooser.launch(chooser)
        } catch (_: Exception) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
    }

    private fun promptServerUrl() {
        val input = EditText(this).apply {
            hint = "https://yourdomain.com"
            setText(if (ServerConfig.isConfigured(this@MainActivity))
                ServerConfig.url(this@MainActivity) else "")
            setSingleLine()
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.server_dialog_title)
            .setMessage(R.string.server_dialog_msg)
            .setView(input)
            .setCancelable(ServerConfig.isConfigured(this))
            .setPositiveButton(R.string.save) { _, _ ->
                if (ServerConfig.save(this, input.text.toString())) {
                    loadStartPage()
                } else {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
                    promptServerUrl()
                }
            }
            .apply {
                if (ServerConfig.isConfigured(this@MainActivity)) {
                    setNegativeButton(R.string.cancel, null)
                }
            }
            .show()
    }

    private fun retry() {
        if (!ServerConfig.isConfigured(this)) {
            promptServerUrl()
            return
        }
        showWeb()
        if (webView.url != null) webView.reload() else loadStartPage()
    }

    private fun showOffline() {
        swipe.isRefreshing = false
        offlineView.visibility = View.VISIBLE
        swipe.visibility = View.GONE
    }

    private fun showWeb() {
        offlineView.visibility = View.GONE
        swipe.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    companion object {
        const val EXTRA_OPEN_PATH = "open_path"
    }
}
