package com.example.visaoprojeto

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.visaoprojeto.ui.theme.VisionProjectTheme
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully.")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        enableEdgeToEdge()
        setContent {
            VisionProjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraScreen(cameraExecutor)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun CameraScreen(executor: ExecutorService) {
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraContent(executor)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required")
        }
    }
}

@Composable
fun CameraContent(executor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    var isProcessing by remember { mutableStateOf(false) }
    var threshold1 by remember { mutableFloatStateOf(50f) }
    var threshold2 by remember { mutableFloatStateOf(150f) }
    
    var lastOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    lastOriginalBitmap = bitmap
                    
                    if (isProcessing) {
                        val result = applyCannyPipeline(bitmap, threshold1.toDouble(), threshold2.toDouble())
                        processedBitmap = result
                    } else {
                        processedBitmap = null
                    }
                    imageProxy.close()
                }
            }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (isProcessing && processedBitmap != null) {
            Image(
                bitmap = processedBitmap!!.asImageBitmap(),
                contentDescription = "Processed Frame",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            if (isProcessing) {
                Text("Canny Threshold 1: ${threshold1.toInt()}", color = Color.White)
                Slider(value = threshold1, onValueChange = { threshold1 = it }, valueRange = 0f..255f)
                Text("Canny Threshold 2: ${threshold2.toInt()}", color = Color.White)
                Slider(value = threshold2, onValueChange = { threshold2 = it }, valueRange = 0f..255f)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    captureAndSaveImage(context, imageCapture, executor)
                }) {
                    Text("Capture")
                }

                Button(onClick = { isProcessing = !isProcessing }) {
                    Text(if (isProcessing) "Stop Process" else "Process")
                }
                
                if (isProcessing) {
                    Button(onClick = {
                        saveAllPipelineSteps(context, lastOriginalBitmap, threshold1.toDouble(), threshold2.toDouble())
                    }) {
                        Text("Save All")
                    }
                }
            }
        }
    }
}

fun applyCannyPipeline(bitmap: Bitmap, t1: Double, t2: Double): Bitmap {
    val rgba = Mat()
    Utils.bitmapToMat(bitmap, rgba)
    
    val gray = Mat()
    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
    
    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
    
    val edges = Mat()
    Imgproc.Canny(blurred, edges, t1, t2)
    
    val resultBitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(edges, resultBitmap)
    
    // Cleanup
    rgba.release()
    gray.release()
    blurred.release()
    edges.release()
    
    return resultBitmap
}

fun captureAndSaveImage(context: Context, imageCapture: ImageCapture, executor: ExecutorService) {
    val name = "Capture_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VisionProject")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        .build()

    imageCapture.takePicture(
        outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d("CameraX", "Photo capture succeeded: ${output.savedUri}")
            }
        }
    )
}

fun saveAllPipelineSteps(context: Context, bitmap: Bitmap?, t1: Double, t2: Double) {
    if (bitmap == null) return
    
    val rgba = Mat()
    Utils.bitmapToMat(bitmap, rgba)
    
    val gray = Mat()
    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
    
    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
    
    val edges = Mat()
    Imgproc.Canny(blurred, edges, t1, t2)
    
    // Convert back to bitmaps
    val grayBitmap = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(gray, grayBitmap)
    
    val blurredBitmap = Bitmap.createBitmap(blurred.cols(), blurred.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(blurred, blurredBitmap)
    
    val edgesBitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(edges, edgesBitmap)
    
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    
    saveBitmapToGallery(context, bitmap, "1_Original_$timeStamp")
    saveBitmapToGallery(context, grayBitmap, "2_Grayscale_$timeStamp")
    saveBitmapToGallery(context, blurredBitmap, "3_Blurred_$timeStamp")
    saveBitmapToGallery(context, edgesBitmap, "4_Edges_$timeStamp")
    
    // Cleanup
    rgba.release()
    gray.release()
    blurred.release()
    edges.release()
    
    Toast.makeText(context, "All 4 images saved to gallery!", Toast.LENGTH_SHORT).show()
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VisionProject")
        }
    }
    
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }
    }
}
