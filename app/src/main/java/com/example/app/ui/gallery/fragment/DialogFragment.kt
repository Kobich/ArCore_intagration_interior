import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.app.R
import com.example.app.ui.preview.PreviewModelActivity
import java.io.File

class ModelPreviewDialogFragment : DialogFragment() {

    interface ModelChangeListener {
        fun onModelChanged()
    }

    companion object {
        private const val ARG_MODEL_NAME = "model_name"
        private const val ARG_MODEL_IMAGE = "model_image"
        private const val ARG_IS_DOWNLOADED = "is_downloaded"
        private const val ARG_MODEL_PATH = "model_path"

        fun newInstance(
            modelName: String,
            modelImage: String,
            isDownloaded: Boolean,
            modelPath: String?
        ): ModelPreviewDialogFragment {
            val fragment = ModelPreviewDialogFragment()
            val args = Bundle()
            args.putString(ARG_MODEL_NAME, modelName)
            args.putString(ARG_MODEL_IMAGE, modelImage)
            args.putBoolean(ARG_IS_DOWNLOADED, isDownloaded)
            args.putString(ARG_MODEL_PATH, modelPath)
            fragment.arguments = args
            return fragment
        }
    }

    private var modelChangeListener: ModelChangeListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        modelChangeListener = parentFragment as? ModelChangeListener
            ?: activity as? ModelChangeListener
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_preview, null)
        val modelName = arguments?.getString(ARG_MODEL_NAME) ?: ""
        val modelImage = arguments?.getString(ARG_MODEL_IMAGE) ?: ""
        val isDownloaded = arguments?.getBoolean(ARG_IS_DOWNLOADED) ?: false
        val modelPath = arguments?.getString(ARG_MODEL_PATH)

        val imageView = view.findViewById<ImageView>(R.id.modelPreviewImage)
        val nameView = view.findViewById<TextView>(R.id.modelName)
        val btnRename = view.findViewById<Button>(R.id.btnRename)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)
        val btnPreview = view.findViewById<Button>(R.id.btnPreview)

        // Загрузка превью
        if (isDownloaded && modelImage.isNotEmpty()) {
            Glide.with(view)
                .load(File(modelImage))
                .placeholder(R.drawable.ic_furniture)
                .into(imageView)
        } else if (!isDownloaded && modelImage.isNotEmpty()) {
            Glide.with(view)
                .load(Uri.parse("file:///android_asset/$modelImage"))
                .placeholder(R.drawable.ic_furniture)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_furniture)
        }

        nameView.text = modelName

        val infoView = view.findViewById<TextView>(R.id.modelInfo)
                if (isDownloaded && modelPath != null) {
                        val file = File(modelPath)
                        val sizeKb = file.length() / 1024
                        val lastModified = file.lastModified()
                        val formattedDate = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastModified))
                        infoView.visibility = View.VISIBLE
                        infoView.text = "Размер: ${sizeKb} КБ\nФайл: ${file.name}\nПуть: ${file.parent}\nЗагружено: $formattedDate"
                    } else {
                        infoView.visibility = View.VISIBLE
                        infoView.text = "Источник: встроенная модель"
                    }


        if (isDownloaded) {
            btnRename.visibility = Button.VISIBLE
            btnDelete.visibility = Button.VISIBLE
        }

        btnRename.setOnClickListener {
            val editText = EditText(context)
            editText.setText(modelName)
            AlertDialog.Builder(context)
                .setTitle("Переименовать модель")
                .setView(editText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newName = editText.text.toString()
                    if (modelPath != null && newName.isNotBlank()) {
                        renameModelFolder(modelPath, newName)
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Удалить модель?")
                .setMessage("Вы уверены, что хотите удалить эту модель?")
                .setPositiveButton("Удалить") { _, _ ->
                    if (modelPath != null) {
                        deleteModelFolder(modelPath)
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        btnPreview.setOnClickListener {
            val intent = Intent(requireContext(), PreviewModelActivity::class.java).apply {
                putExtra("MODEL_PATH", modelPath) // Pass the model path from your model
            }
            startActivity(intent)
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    private fun renameModelFolder(modelPath: String, newName: String) {
        val modelFile = File(modelPath)
        val parentDir = modelFile.parentFile ?: return
        val modelsRoot = parentDir.parentFile ?: return
        val newFolder = File(modelsRoot, newName)
        if (parentDir.renameTo(newFolder)) {
            // Переименовать GLB и PNG внутри папки
            newFolder.listFiles()?.forEach { file ->
                if (file.extension == "glb" && file.nameWithoutExtension != newName) {
                    file.renameTo(File(newFolder, "$newName.glb"))
                }
                if (file.extension == "png" && file.nameWithoutExtension != newName) {
                    file.renameTo(File(newFolder, "$newName.png"))
                }
            }
            modelChangeListener?.onModelChanged()
            dismiss()
        } else {
            AlertDialog.Builder(context)
                .setTitle("Ошибка")
                .setMessage("Не удалось переименовать модель.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun deleteModelFolder(modelPath: String) {
        val modelFile = File(modelPath)
        val parentDir = modelFile.parentFile ?: return
        parentDir.deleteRecursively()
        modelChangeListener?.onModelChanged()
        dismiss()
    }
}