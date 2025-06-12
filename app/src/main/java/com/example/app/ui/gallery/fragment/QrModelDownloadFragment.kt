package com.example.app.ui.gallery.fragment

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
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
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.ViewfinderView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class QrModelDownloadFragment : Fragment() {
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private val client = OkHttpClient()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_qr_model_download, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        barcodeScannerView = view.findViewById(R.id.zxing_barcode_scanner)


        val back = view.findViewById<ImageView>(R.id.btn_back)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        progressBar.isIndeterminate = true
        progressBar.indeterminateTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
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
            if (text.startsWith("http")) downloadModel(text)
            else {
                Toast.makeText(requireContext(), "Неверный QR-код", Toast.LENGTH_SHORT).show()
                barcodeScannerView.resume()
            }
        }
        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
    }


    private fun downloadModel(url: String) {
        val progressBar = view?.findViewById<ProgressBar>(R.id.progress_bar)
        progressBar?.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) throw Exception("Код ${response.code}")
                val name = Uri.parse(url).lastPathSegment ?: "model.glb"
                val file = File(requireContext().cacheDir, name)
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                delay(1500)
                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Модель: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    barcodeScannerView.resume()
                }
            }
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

    companion object {
        fun newInstance() = QrModelDownloadFragment()
    }

}
