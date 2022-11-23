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
    private var manual = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        result = intent.getStringArrayListExtra("result") as ArrayList<String>
        manual = intent.getBooleanExtra("manual", false)
        setupResult()
        setupExtractor()
    }

    private fun setupResult(){
        binding.resultText.text = result.joinToString(System.lineSeparator())
        binding.extractFab.setOnClickListener {
            downloadExtractor()
        }
        if(manual){
            setupManualLayout()
        }
    }

    private fun setupManualLayout(){
        with(binding){
            autoExtractionTextLayout.visibility = View.GONE
            manualExtractionTextLayout.visibility = View.VISIBLE
            try {
                cognome.text = result[result.indexOf(TextRecognitionActivity.InfoType.COGNOME.name)+1]
                nome.text = result[result.indexOf(TextRecognitionActivity.InfoType.NOME.name)+1]
                dataluogo.text = result[result.indexOf(TextRecognitionActivity.InfoType.DATA_LUOGO_NASCITA.name)+1]
                rilascio.text = result[result.indexOf(TextRecognitionActivity.InfoType.DATA_RILASCIO.name)+1].replace("4c.", "da")
                scadenza.text = result[result.indexOf(TextRecognitionActivity.InfoType.SCADENZA.name)+1]
                numero.text = result[result.indexOf(TextRecognitionActivity.InfoType.NUMERO.name)+1]
                tipo.text = result[result.indexOf(TextRecognitionActivity.InfoType.TIPO.name)+1]
            }catch (e: Exception){

            }
        }
    }

    private fun setupExtractor(){
        extractor = EntityExtraction.getClient(
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
        val params = EntityExtractionParams.Builder(result.joinToString(" ")).build()
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

            for (entity in entities) {
                when (entity) {
                    is PaymentCardEntity -> {
                        binding.cardNumber.text = entity.paymentCardNumber
                        binding.cardCircuit.text = entity.paymentCardNetwork.toString()
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