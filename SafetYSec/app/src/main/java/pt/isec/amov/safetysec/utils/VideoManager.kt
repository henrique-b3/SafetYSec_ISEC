package pt.isec.amov.safetysec.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class VideoManager(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Flag para controlar o cancelamento
    private var isCancelled = false

    @SuppressLint("MissingPermission")
    fun startRecording(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        alertId: String,
        onVideoUploaded: (String) -> Unit
    ) {
        isCancelled = false // Reset na flag

        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val qualitySelector = QualitySelector.from(Quality.LOWEST)
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture)
                recordVideo(alertId, onVideoUploaded)
            } catch (exc: Exception) {
                Log.e("SafetySecVideo", "Erro câmara", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("MissingPermission")
    private fun recordVideo(alertId: String, onVideoUploaded: (String) -> Unit) {
        val videoCapture = this.videoCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".mp4"
        val file = File(context.cacheDir, name)
        val outputOptions = FileOutputOptions.Builder(file).build()

        recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (recording != null) stopRecording()
                        }, 30000)
                    }
                    is VideoRecordEvent.Finalize -> {
                        val isSourceInactiveError = recordEvent.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE

                        if (!isCancelled && (!recordEvent.hasError() || isSourceInactiveError)) {
                            Log.d("SafetySecVideo", "Gravação finalizada com sucesso (ou interrupção aceitável). A enviar...")
                            uploadVideo(file, alertId, onVideoUploaded)
                        } else {
                            file.delete()
                            Log.d("SafetySecVideo", "Gravação cancelada ou erro crítico: ${recordEvent.error}. Ficheiro apagado.")
                        }
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    // --- NOVA FUNÇÃO CHAMADA PELO ECRÃ ---
    fun cancelRecording() {
        isCancelled = true
        recording?.stop()
        recording = null
    }

    private fun uploadVideo(file: File, alertId: String, onVideoUploaded: (String) -> Unit) {
        Log.d("SafetySecVideo", "A iniciar upload...")
        val storageRef = storage.reference.child("alert_videos/${file.name}")
        val uploadTask = storageRef.putFile(Uri.fromFile(file))

        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                val downloadUrl = uri.toString()
                db.collection("alerts").document(alertId)
                    .update("videoUrl", downloadUrl)
                    .addOnSuccessListener { onVideoUploaded(downloadUrl) }
            }
        }.addOnFailureListener {
            Log.e("SafetySecVideo", "Falha no upload", it)
        }
    }
}