package org.hyperskill.photoeditor

import android.Manifest
import android.R.drawable
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.*
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.graphics.blue
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.pow


class MainActivity : AppCompatActivity() {

    private lateinit var currentImage: ImageView
    private lateinit var galleryButton: Button
    private lateinit var saveButton: Button
    private lateinit var brightnessSlider: Slider
    private lateinit var contrastSlider: Slider
    private lateinit var saturationSlider: Slider
    private lateinit var gammaSlider: Slider
    private var defaultBitmap = createBitmap()

    private var lastJob: Job? = null


    //private val requestPermission =
    //    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
    //        saveButton.callOnClick()
    //    }

    private val activityResultLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val photoUri = result.data?.data ?: return@registerForActivityResult
                currentImage.setImageURI(photoUri)
                defaultBitmap = currentImage.drawable.toBitmap()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        galleryButton.setOnClickListener {
            activityResultLauncher
                .launch(Intent(Intent.ACTION_PICK, Images.Media.EXTERNAL_CONTENT_URI))
        }

        saveButton.setOnClickListener {
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                val bitmap: Bitmap = currentImage.drawable.toBitmap()
                val values = ContentValues()
                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
                values.put(Images.Media.MIME_TYPE, "image/jpeg")
                values.put(Images.ImageColumns.WIDTH, bitmap.width)
                values.put(Images.ImageColumns.HEIGHT, bitmap.height)

                val uri = this@MainActivity.contentResolver.insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@setOnClickListener

                contentResolver.openOutputStream(uri).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                }
            } else {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                //requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        brightnessSlider.addOnChangeListener { slider, value, fromUser ->
            applyFilters()
        }

        contrastSlider.addOnChangeListener { slider, value, fromUser ->
            applyFilters()
        }

        saturationSlider.addOnChangeListener { slider, value, fromUser ->
            applyFilters()
        }

        gammaSlider.addOnChangeListener { slider, value, fromUser ->
            applyFilters()
        }

        //do not change this line
        currentImage.setImageBitmap(createBitmap())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveButton.callOnClick()
        }
    }

    private fun hasPermission(manifestPermission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.checkSelfPermission(manifestPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            PermissionChecker.checkSelfPermission(this, manifestPermission) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun bindViews() {
        currentImage = findViewById(R.id.ivPhoto)
        galleryButton = findViewById(R.id.btnGallery)
        saveButton = findViewById(R.id.btnSave)
        brightnessSlider = findViewById(R.id.slBrightness)
        contrastSlider = findViewById(R.id.slContrast)
        saturationSlider = findViewById(R.id.slSaturation)
        gammaSlider = findViewById(R.id.slGamma)
    }

    private fun applyFilters() {
        /*
        filter("brightness", defaultBitmap)
        filter("contrast", currentImage.drawable.toBitmap())
        filter("saturation", currentImage.drawable.toBitmap())
        filter("gamma", currentImage.drawable.toBitmap())

         */
        filters()
    }

    private fun findAvgBrightness(bitmap: Bitmap): Int {
        var totalBrightness: Long = 0
        val width = bitmap.width
        val height = bitmap.height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = bitmap.getPixel(x, y)
                totalBrightness += color.red + color.green + color.blue
            }
        }
        return (totalBrightness / (height * width * 3)).toInt()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun filters() {
        lastJob?.cancel()

        lastJob = GlobalScope.launch(Dispatchers.Default) {

            val brightenCopyDeferred: Deferred<Bitmap> = this.async {
                filter("brightness", defaultBitmap)
            }
            val brightenCopy: Bitmap = brightenCopyDeferred.await()

            val contrastedCopy = filter("contrast", brightenCopy)
            ensureActive()

            val saturatedCopy = filter("saturation", contrastedCopy)
            ensureActive()

            val gammaCopy = filter("gamma", saturatedCopy)
            ensureActive()

            runOnUiThread {
                currentImage.setImageBitmap(gammaCopy)
            }
        }
    }

    private fun filter(filterType: String, bitmap: Bitmap): Bitmap {
        //val bitmap = if (filterType == "brightness") defaultBitmap else currentImage.drawable.toBitmap()
        val width = bitmap.width
        val height = bitmap.height
        val value = when (filterType) {
            "brightness" -> brightnessSlider.value
            "contrast" -> contrastSlider.value
            "saturation" -> saturationSlider.value
            "gamma" -> gammaSlider.value
            else -> return bitmap
        }
        val alpha: Double = ((255 + value) / (255 - value)).toDouble()
        val pixels = IntArray(width * height)

        // only calculate avgBrightness for contrast filter
        val avgBrightness = if (filterType == "contrast") findAvgBrightness(bitmap) else 0

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                val color = bitmap.getPixel(x, y)
                // adjust colors
                when (filterType) {
                    "brightness" -> {
                        R = color.red + value.toInt()
                        G = color.green + value.toInt()
                        B = color.blue + value.toInt()
                    }
                    "contrast" -> {
                        R = (alpha * (color.red - avgBrightness) + avgBrightness).toInt()
                        G = (alpha * (color.green - avgBrightness) + avgBrightness).toInt()
                        B = (alpha * (color.blue - avgBrightness) + avgBrightness).toInt()
                    }
                    "saturation" -> {
                        val rgbAvg = (color.red + color.blue + color.green) / 3
                        R = (alpha * (color.red - rgbAvg) + rgbAvg).toInt()
                        G = (alpha * (color.green - rgbAvg) + rgbAvg).toInt()
                        B = (alpha * (color.blue - rgbAvg) + rgbAvg).toInt()
                    }
                    "gamma" -> {
                        R = (255 * (color.red / 255.0).pow(value.toDouble())).toInt()
                        G = (255 * (color.green / 255.0).pow(value.toDouble())).toInt()
                        B = (255 * (color.blue / 255.0).pow(value.toDouble())).toInt()
                    }
                    else -> return bitmap
                }

                // check bounds
                if (R > 255)
                    R = 255
                if (R < 0)
                    R = 0
                if (G > 255)
                    G = 255
                if (G < 0)
                    G = 0
                if (B > 255)
                    B = 255
                if (B < 0)
                    B = 0

                pixels[index] = Color.rgb(R,G,B)
            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        //currentImage.setImageBitmap(bitmapOut)
        return bitmapOut
    }

    // do not change this function
    fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        // get pixel array from source

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                // get color
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x+y) % 100 + 120

                pixels[index] = Color.rgb(R,G,B)
            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }
}