package com.mlkit.cdc2022

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.mlkit.nl.entityextraction.*
import com.mlkit.cdc2022.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var result: ArrayList<String>
    private lateinit var extractor: EntityExtractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        result = intent.getStringArrayListExtra("result") as ArrayList<String>

        setupResult()
        setupExtractor()
    }

    private fun setupResult(){
        binding.resultText.text = result.joinToString(System.lineSeparator())
        binding.extractFab.setOnClickListener {
            downloadExtractor()
        }
    }

    private fun setupExtractor(){
        extractor =
            EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
                    .build())


    }

    private fun downloadExtractor(){
        extractor
            .downloadModelIfNeeded()
            .addOnSuccessListener { _ ->
                extractInfo()
            }
            .addOnFailureListener { e ->
                Log.d("Error:","extraction text -> $e")
            }
    }

    private fun extractInfo(){
        val params =
            EntityExtractionParams.Builder(result.joinToString(" "))
                    .build()

        extractor
            .annotate(params)
            .addOnSuccessListener {
                processInfo(it)
            }
            .addOnFailureListener {
                // Check failure message here.
            }
    }

    private fun processInfo(entityAnnotations: MutableList<EntityAnnotation>){
        for (entityAnnotation in entityAnnotations) {
            val entities: List<Entity> = entityAnnotation.entities

            Log.d(TAG, "Range: ${entityAnnotation.start} - ${entityAnnotation.end}")
            for (entity in entities) {
                when (entity) {
                    is PaymentCardEntity -> {
                        Log.d(TAG, "Card number: ${entity.paymentCardNumber}")
                        Log.d(TAG, "Card network: ${entity.paymentCardNetwork}")
                        binding.cardNumber.text = entity.paymentCardNumber
                        binding.cardCircuit.text = entity.paymentCardNetwork.toString()
                        binding.resultText.visibility = View.GONE
                    }

                    else -> {
                        Log.d(TAG, "  $entity")
                    }
                }
            }
        }
    }

    companion object{
        const val TAG = "RESULT_ACTIVITY"
    }

}