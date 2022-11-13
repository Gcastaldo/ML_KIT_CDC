package com.mlkit.cdc2022

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mlkit.cdc2022.databinding.ActivityTextRecognitionBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class TextRecognitionActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        const val TAG = "MLKit_Activity"
    }

    private lateinit var binding: ActivityTextRecognitionBinding
    private lateinit var currentPhotoPath: String
    private var capturedBitmap: Bitmap? = null
    private var visionText: Text? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextRecognitionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.captureImageFab.setOnClickListener {
            takePhoto()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE &&
            resultCode == Activity.RESULT_OK
        ) {
            getCapturedImage()
            binding.captureImageFab.text = getString(R.string.image_process)
            binding.captureImageFab.setOnClickListener {
                processImage()
            }
        }
    }

    private fun processImage() {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = capturedBitmap?.let { InputImage.fromBitmap(it, 0) }
        val list = arrayListOf<String>()
        val result = image?.let {
            recognizer.process(it)
                .addOnSuccessListener { text ->
                    // [START get_text]
                    visionText = text
                    text.textBlocks.forEachIndexed { i, block ->
                        val boundingBox = block.boundingBox
                        val cornerPoints = block.cornerPoints
                        val text = block.text
                        block.lines.forEachIndexed { j, line ->
                            val lineText = line.text
                            val lineCornerPoints = line.cornerPoints
                            val lineFrame = line.boundingBox
                            list.add(lineText)
                            line.elements.forEachIndexed { k, element ->
                                val elementText = element.text
                                val elementCornerPoints = element.cornerPoints
                                val elementFrame = element.boundingBox

                            }
                        }
                    }
                   val intent = Intent(this, ResultActivity::class.java)
                    intent.putStringArrayListExtra("result", list)
                    startActivity(intent)
                    // [END get_text]
                }
                .addOnFailureListener { e ->
                    Log.d("Error processing:", "image detect text -> $e")
                }
        }
    }

    private fun getCapturedImage(){
        // Get the dimensions of the View
        val targetW: Int = binding.previewImage.width
        val targetH: Int = binding.previewImage.height

        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // Determine how much to scale down the image
            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val bitmap = BitmapFactory.decodeFile(currentPhotoPath)

        runObjectDetection(bitmap)

    }

    private fun runObjectDetection(bitmap: Bitmap): Bitmap {
        val image = InputImage.fromBitmap(bitmap, 0)
        //init bitmap
        var outputBitmap = bitmap

        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        val objectDetector = ObjectDetection.getClient(options)

        objectDetector.process(image).addOnSuccessListener { results ->

            // Parse ML Kit's DetectedObject and create corresponding visualization data
            val detectedObjects = results.map {
                var text = "Unknown"

                // We will show the top confident detection result if it exist
                if (it.labels.isNotEmpty()) {
                    val firstLabel = it.labels.first()
                    text = "${firstLabel.text}, ${firstLabel.confidence.times(100).toInt()}%"
                }
                BoxWithText(it.boundingBox, text)
            }

            val visulizedResult = cropItemDetected(bitmap, detectedObjects)
            capturedBitmap = visulizedResult
            binding.previewImage.setImageBitmap(visulizedResult)
            outputBitmap = visulizedResult
        }.addOnFailureListener {
            Log.e(TAG, it.message.toString())
        }
        return outputBitmap
    }

    private fun cropItemDetected(bitmap: Bitmap, detectedObjects: List<BoxWithText>): Bitmap {
        val left = detectedObjects[0].box.left
        val right = detectedObjects[0].box.right
        val bottom = detectedObjects[0].box.bottom
        val top = detectedObjects[0].box.top
        var outputBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)

        return outputBitmap
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    private fun takePhoto() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.google.mlkit.cdc2022.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }
}

data class BoxWithText(val box: Rect, val text: String)