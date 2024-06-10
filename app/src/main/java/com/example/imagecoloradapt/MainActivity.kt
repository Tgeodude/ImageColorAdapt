package com.example.imagecoloradapt

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.imagecoloradapt.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var imageView: ImageView
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_PICK_IMAGE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageView = binding.imageView
        val takePhotoButton: Button = binding.takePicture
        val pickImageButton: Button = binding.gallery

        takePhotoButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

        pickImageButton.setOnClickListener {
            dispatchPickImageIntent()
        }

        // Check and request permissions if necessary
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_IMAGE_CAPTURE)
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PICK_IMAGE
            )
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    private fun dispatchPickImageIntent() {
        val pickImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(pickImageIntent, REQUEST_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val imageBitmap = data?.extras?.get("data") as? Bitmap
                    val imageUri = getImageUriFromBitmap(imageBitmap)
                    val orientation = getImageOrientation(imageUri)
                    val adjustedBitmap = adjustBrightness(imageBitmap, orientation)
                    imageView.setImageBitmap(adjustedBitmap)
                }

                REQUEST_PICK_IMAGE -> {
                    val selectedImageUri: Uri? = data?.data
                    val imageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImageUri)
                    val orientation = getImageOrientation(selectedImageUri!!)
                    val adjustedBitmap = adjustBrightness(imageBitmap, orientation)
                    imageView.setImageBitmap(adjustedBitmap)
                }
            }
        }
    }

    private fun getImageOrientation(imageUri: Uri): Int {
        val inputStream = contentResolver.openInputStream(imageUri)
        val exif = ExifInterface(inputStream!!)
        return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    }

    private fun adjustBrightness(image: Bitmap?, orientation: Int): Bitmap? {
        if (image == null) return null

        // Ваши входные данные для преобразования изображения
        val maxPixelBrightness = getMaxPixelBrightness(image) // Предполагаемая максимальная яркость точки на изображении
        val screenBrightness = getScreenBrightnessPercentage(binding.root.context) // Яркость экрана в процентах
        val imageWhitePoint = getImageWhitePoint(image) // Точка белого на изображении
        val screenWhitePoint = 120 // Точка белого на экране

        // Применяем преобразование к изображению
        return adjustBrightness(rotateBitmap(image, orientation), maxPixelBrightness, screenBrightness, imageWhitePoint, screenWhitePoint)
    }

    private fun getScreenBrightnessPercentage(context: Context): Int {
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        return (brightness * 100) / 255
    }

    private fun adjustBrightness(image: Bitmap, maxPixelBrightness: Int, screenBrightness: Int, imageWhitePoint: Int, screenWhitePoint: Int): Bitmap {
        val adjustedBitmap = image.copy(image.config, true)

        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image.getPixel(x, y)

                // Получаем компоненты цвета
                val alpha = pixel shr 24 and 0xff
                var red = pixel shr 16 and 0xff
                var green = pixel shr 8 and 0xff
                var blue = pixel and 0xff

                // Корректируем яркость каждого пикселя
                red = (red * (screenBrightness / 100.0) * (imageWhitePoint.toDouble() / screenWhitePoint)).toInt()
                green = (green * (screenBrightness / 100.0) * (imageWhitePoint.toDouble() / screenWhitePoint)).toInt()
                blue = (blue * (screenBrightness / 100.0) * (imageWhitePoint.toDouble() / screenWhitePoint)).toInt()

                // Ограничиваем значения в пределах [0, maxPixelBrightness]
                red = red.coerceIn(0, maxPixelBrightness)
                green = green.coerceIn(0, maxPixelBrightness)
                blue = blue.coerceIn(0, maxPixelBrightness)

                // Устанавливаем новый цвет пикселя
                adjustedBitmap.setPixel(x, y, alpha shl 24 or (red shl 16) or (green shl 8) or blue)
            }
        }

        return adjustedBitmap
    }

    private fun rotateBitmap(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap?): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Title", null)
        return Uri.parse(path)
    }

    private fun getMaxPixelBrightness(image: Bitmap): Int {
        var maxBrightness = 0

        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (brightness > maxBrightness) {
                    maxBrightness = brightness
                }
            }
        }

        return maxBrightness
    }

    private fun getImageWhitePoint(image: Bitmap): Int {
        var totalBrightness = 0
        var totalPixels = 0

        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                // Проверяем, насколько пиксель близок к белому цвету
                val threshold = 50 // Пороговое значение для определения белого цвета
                if (red > 255 - threshold && green > 255 - threshold && blue > 255 - threshold) {
                    val brightness = (red + green + blue) / 3
                    totalBrightness += brightness
                    totalPixels++
                }
            }
        }

        // Находим среднюю яркость пикселей белого цвета
        return if (totalPixels != 0) totalBrightness / totalPixels else 255 // Возвращаем 255, если не найдено пикселей белого цвета
    }
}
