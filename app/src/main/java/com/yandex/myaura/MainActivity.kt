package com.yandex.myaura

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private var mUploadMessage: ValueCallback<Uri>? = null
    private var mCapturedImageURI: Uri? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null
    private lateinit var browser: WebView
    internal lateinit var swipe: SwipeRefreshLayout

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            var results: Array<Uri>? = null
            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else {
                    val dataString = data.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
            mFilePathCallback!!.onReceiveValue(results)
            mFilePathCallback = null
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (requestCode != FILECHOOSER_RESULTCODE || mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            if (requestCode == FILECHOOSER_RESULTCODE) {
                if (null == this.mUploadMessage) {
                    return
                }
                var result: Uri? = null
                try {
                    if (resultCode != Activity.RESULT_OK) {
                        result = null
                    } else {
                        // retrieve from the private variable if the intent is null
                        result = if (data == null) mCapturedImageURI else data.data
                    }
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "activity :$e",
                            Toast.LENGTH_LONG).show()
                }

                mUploadMessage!!.onReceiveValue(result)
                mUploadMessage = null
            }
        }
        return
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipe = findViewById<View>(R.id.swipe) as SwipeRefreshLayout
        swipe.setOnRefreshListener { WebAction() }
        WebAction()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
                imageFileName, /* prefix */
                ".jpg", /* suffix */
                storageDir      /* directory */
        )
    }

    fun WebAction() {
        browser = findViewById<View>(R.id.webView) as WebView
        this.registerForContextMenu(browser)
        val browserSettings = browser.settings
        browserSettings.javaScriptEnabled = true
        browserSettings.loadWithOverviewMode = true
        browserSettings.setAppCacheEnabled(true)
        browserSettings.domStorageEnabled = true
        browserSettings.allowFileAccess = true
        browserSettings.builtInZoomControls = true
        browserSettings.userAgentString = userAgent
        browser.loadUrl(loadUrl)
        swipe.isRefreshing = false
        swipe.isEnabled = false
        browser.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                swipe.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (!url.contains("yandex")) {
                    view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                } else {
                    view.loadUrl(url)
                    return true
                }
            }
        }

        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(webView: WebView, filePath: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
                // Double check that we don't have any existing callbacks
                if (mFilePathCallback != null) {
                    mFilePathCallback!!.onReceiveValue(null)
                }
                mFilePathCallback = filePath
                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent!!.resolveActivity(packageManager) != null) {
                    // Create the File where the photo should go
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                    } catch (ex: IOException) {
                        // Error occurred while creating the File
                        Log.e(TAG, "Unable to create Image File", ex)
                    }

                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.absolutePath
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                                Uri.fromFile(photoFile))
                    } else {
                        takePictureIntent = null
                    }
                }
                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "image/*"
                val intentArray: Array<Intent?>
                intentArray = (if (takePictureIntent != null) {
                    arrayOf(takePictureIntent)
                } else arrayOfNulls(0))
                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE)
                return true
            }

            // openFileChooser for Android 3.0+
            fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String = "") {
                mUploadMessage = uploadMsg
                // Create Aura folder at sdcard
                val imageStorageDir = File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES), "Aura")
                if (!imageStorageDir.exists()) {
                    // Create AndroidExampleFolder at sdcard
                    imageStorageDir.mkdirs()
                }
                // Create camera captured image file path and name
                val file = File(
                        imageStorageDir.toString() + File.separator + "IMG_"
                                + System.currentTimeMillis().toString()
                                + ".jpg")
                mCapturedImageURI = Uri.fromFile(file)
                // Camera capture image intent
                val captureIntent = Intent(
                        android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI)
                val i = Intent(Intent.ACTION_GET_CONTENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "image/*"
                // Create file chooser intent
                val chooserIntent = Intent.createChooser(i, "Image Chooser")
                // Set camera intent to file chooser
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(captureIntent))
                // On select image call onActivityResult method of activity
                startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE)
            }

            //openFileChooser for other Android versions
            fun openFileChooser(uploadMsg: ValueCallback<Uri>,
                                acceptType: String,
                                capture: String) {
                openFileChooser(uploadMsg, acceptType)
            }
        }
    }

    override fun onBackPressed() {
        if (browser.canGoBack()) {
            browser.goBack()
        } else {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_bar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> browser.reload()
            R.id.action_previous_post -> browser.goBack()
        }
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)

        val w = v as WebView
        val result = w.hitTestResult
        if (result.type == WebView.HitTestResult.IMAGE_TYPE) {
            menu.addSubMenu(1, 1, 1, R.string.save_picture)
            menu.addSubMenu(1, 2, 2, R.string.share_image)
        }
    }

    companion object {
        private const val INPUT_FILE_REQUEST_CODE = 1
        private const val FILECHOOSER_RESULTCODE = 1
        private val TAG = MainActivity::class.java.simpleName

        private const val loadUrl = "https://yandex.ru/aura/"
        private const val userAgent = "Mozilla/5.0 (Linux; Android 9; Mi A2 Build/PKQ1.180904.001; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/72.0.3626.121 " +
                "Mobile Safari/537.36 YandexSearch/8.05 YandexSearchBrowser/8.05"
    }
}
