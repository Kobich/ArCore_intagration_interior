package com.example.app.ui.gallery.fragment

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class QrModelDownloadFragment : Fragment() {

    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_qr_model_download, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barcodeScannerView = view.findViewById(R.id.zxing_barcode_scanner)
        val back = view.findViewById<ImageView>(R.id.btn_back)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)

        progressBar.isIndeterminate = true
        progressBar.indeterminateTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        )

        barcodeScannerView.decodeContinuous(callback)
        barcodeScannerView.post { barcodeScannerView.resume() }

        back.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }


    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            barcodeScannerView.pause()
            val text = result.text
            if (text.startsWith("http")) {
                downloadModel(text)
            } else {
                Toast.makeText(requireContext(), "Неверный QR-код", Toast.LENGTH_SHORT).show()
                barcodeScannerView.resume()
            }
        }

        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
    }

    private fun downloadModel(pageUrl: String) {
        val progressBar = view?.findViewById<ProgressBar>(R.id.progress_bar)
        progressBar?.visibility = View.VISIBLE
        Log.d("QR_DOWNLOAD", "Начало загрузки модели с URL: $pageUrl")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val htmlResponse = client.newCall(createHtmlRequest(pageUrl)).execute()

                if (!htmlResponse.isSuccessful) throw Exception("HTML HTTP ${htmlResponse.code}")
                val bodyText = htmlResponse.body?.charStream()?.readText()
                    ?: throw Exception("Empty HTML")

                Log.d("QR_DOWNLOAD", "HTML загружен, длина: ${bodyText.length}")
                val actualModelUrl = resolveGlbUrlFromHtml(pageUrl, bodyText)
                    ?: throw Exception("GLB ссылка не найдена")

                Log.d("QR_DOWNLOAD", "GLB URL: $actualModelUrl")
                val modelResponse = client.newCall(createModelRequest(actualModelUrl, pageUrl)).execute()

                if (!modelResponse.isSuccessful) throw Exception("GLB HTTP ${modelResponse.code}")
                saveModelFile(modelResponse, actualModelUrl)

                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Модель загружена", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                handleDownloadError(e, progressBar)
            }
        }
    }

    private fun createHtmlRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0")
        .header("Referer", url)
        .header("Accept", "*/*")
        .build()

    private fun createModelRequest(modelUrl: String, referer: String) = Request.Builder()
        .url(modelUrl)
        .header("User-Agent", "Mozilla/5.0")
        .header("Referer", referer)
        .build()

    private fun saveModelFile(response: okhttp3.Response, modelUrl: String) {
        val contentLength = response.body?.contentLength() ?: -1
        Log.d("QR_DOWNLOAD", "Размер модели: $contentLength байт")

        val rawName = Uri.parse(modelUrl).lastPathSegment ?: "model"
        val fileName = if (rawName.endsWith(".glb", ignoreCase = true)) rawName else "$rawName.glb"
        val folderName = fileName.substringBefore('.')

        val modelDir = File(requireContext().filesDir, "downloaded_models/$folderName").apply {
            mkdirs()
        }
        val modelFile = File(modelDir, fileName)

        val bytesCopied = response.body?.byteStream()?.use { input ->
            FileOutputStream(modelFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Скачивание не удалось")

        Log.d("QR_DOWNLOAD", "Файл сохранен: ${modelFile.absolutePath}, скопировано байт: $bytesCopied")
    }

    private suspend fun handleDownloadError(e: Exception, progressBar: ProgressBar?) {
        withContext(Dispatchers.Main) {
            progressBar?.visibility = View.GONE
            Log.e("QR_DOWNLOAD", "Ошибка загрузки", e)
            Toast.makeText(
                requireContext(),
                "Ошибка: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            barcodeScannerView.resume()
        }
    }

    private fun resolveGlbUrlFromHtml(baseUrl: String, html: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val modelSrc = doc.selectFirst("model-viewer")?.attr("src")
            ?: doc.select("a[href$=.glb]").firstOrNull()?.attr("href")
            ?: return null

        return try {
            val resolved = URL(URL(baseUrl), modelSrc).toString()
            resolved.substringBefore(".glb", "") + ".glb"
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeScannerView.pause()
    }
}