package com.example.app.ui.gallery.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.databinding.FragmentUrlDownloadBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class UrlDownloadFragment : Fragment() {
    private var _binding: FragmentUrlDownloadBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUrlDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.downloadButton.setOnClickListener {
            val url = binding.urlEditText.text.toString().trim()
            if (url.isNotEmpty()) {
                downloadModel(url)
            } else {
                binding.urlInputLayout.error = "Введите URL"
            }
        }
    }

    private fun downloadModel(url: String) {
        if (!url.startsWith("http")) {
            binding.urlInputLayout.error = "Некорректный URL"
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.downloadButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelResponse = client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                ).execute()

                if (!modelResponse.isSuccessful) {
                    throw Exception("Ошибка загрузки: HTTP ${modelResponse.code}")
                }

                val contentLength = modelResponse.body?.contentLength() ?: -1
                Log.d("URL_DOWNLOAD", "Размер модели: $contentLength байт")

                val uri = URL(url).toURI()
                val fileName = uri.path?.substringAfterLast('/') ?: "model.glb"
                val modelDir = File(
                    requireContext().filesDir,
                    "downloaded_models/${fileName.substringBeforeLast('.')}"
                ).apply { mkdirs() }

                val modelFile = File(modelDir, fileName)

                modelResponse.body?.byteStream()?.use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Ошибка сохранения файла")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Модель успешно загружена",
                        Toast.LENGTH_SHORT
                    ).show()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.downloadButton.isEnabled = true
                    binding.urlInputLayout.error = e.message ?: "Ошибка загрузки"
                    Log.e("URL_DOWNLOAD", "Ошибка загрузки", e)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = UrlDownloadFragment()
    }
}