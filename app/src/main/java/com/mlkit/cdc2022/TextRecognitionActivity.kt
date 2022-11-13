package com.mlkit.cdc2022

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
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
            capturedBitmap = getCapturedImage()
            if (capturedBitmap != null) {
                binding.retryFab.visibility = View.VISIBLE
                binding.retryFab.setOnClickListener {
                    takePhoto()
                }
                binding.textDescription.text = getString(R.string.description_process)
                binding.captureImageFab.text = getString(R.string.image_process)
                binding.captureImageFab.setCompoundDrawables(
                    null,
                    null,
                    getDrawable(R.drawable.ic_image),
                    null
                )
                binding.captureImageFab.setOnClickListener {
                    processImage()
                }
            } else {
                Toast.makeText(this, "Error in object detection, retry camera!", Toast.LENGTH_LONG)
                    .show()
                takePhoto()
            }
        }
    }

    private fun processImage(){
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = capturedBitmap?.let { InputImage.fromBitmap(it, 0) }
        val list = arrayListOf<String>()
        val result = image?.let {
            recognizer.process(it)
                .addOnSuccessListener { visionText ->
                    for (block in visionText.textBlocks) {
                        val boundingBox = block.boundingBox
                        val cornerPoints = block.cornerPoints
                        val text = block.text
                        for (line in block.lines) {
                            val lineText = line.text
                            list.add(lineText)
                            val lineCornerPoints = line.cornerPoints
                            val lineFrame = line.boundingBox
                            for (element in line.elements) {
                                val elementText = element.text
                                val elementCornerPoints = element.cornerPoints
                                val elementFrame = element.boundingBox
                            }
                        }
                    }
                    val intent = Intent(this,ResultActivity::class.java)
                    intent.putStringArrayListExtra("result", list)
                    startActivity(intent)
                }
                .addOnFailureListener { e ->
                   Log.d("Error processing:","image detect text -> $e")
                }
        }
    }

    private fun getCapturedImage(): Bitmap? {
        val targetW: Int = binding.previewImage.width
        val targetH: Int = binding.previewImage.height

        val bmOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val bitmap = BitmapFactory.decodeFile(currentPhotoPath)

        val bitmapResult = runObjectDetection(bitmap)

        return bitmapResult
    }

    private fun runObjectDetection(bitmap: Bitmap): Bitmap? {
        val image = InputImage.fromBitmap(bitmap, 0)

        var outputBitmap: Bitmap? = bitmap

        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        val objectDetector = ObjectDetection.getClient(options)

        objectDetector.process(image).addOnSuccessListener { results ->
            if(results.isNotEmpty()) {
                val visulizedResult = cropItemDetected(bitmap, results)
                binding.previewImage.setImageBitmap(visulizedResult)
                outputBitmap = visulizedResult
            }else{
                outputBitmap = null
            }
        }.addOnFailureListener {
            Log.e(TAG, it.message.toString())
            outputBitmap = null
        }
        return outputBitmap
    }

    private fun cropItemDetected(bitmap: Bitmap, detectedObjects: MutableList<DetectedObject>): Bitmap {
        val left = detectedObjects[0].boundingBox.left
        val right = detectedObjects[0].boundingBox.right
        val bottom = detectedObjects[0].boundingBox.bottom
        val top = detectedObjects[0].boundingBox.top
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

    private fun takePhoto(){
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
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
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }
}