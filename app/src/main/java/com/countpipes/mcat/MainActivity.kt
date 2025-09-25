package com.countpipes.mcat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ai.onnxruntime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import com.google.android.material.textfield.TextInputEditText

// Clase auxiliar para detecciones
data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float
)

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var capturedImage: ImageView
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var btnTakePhoto: Button
    private lateinit var btnRetake: Button
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar UI
        previewView = findViewById(R.id.previewView)
        capturedImage = findViewById(R.id.capturedImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)

        val spinnerCentro: Spinner = findViewById(R.id.spinnerCentro)
        val listaCentros = leerCentrosDesdeCSV()

        val inputMaterial: TextInputEditText = findViewById(R.id.inputMaterial)
        val labelDescripcion: TextView = findViewById(R.id.labelDescripcion)

        // Cargar modelo ONNX
        val modeloBytes = cargarModeloONNX()
        if (modeloBytes != null) {
            inicializarONNX(modeloBytes)
        }

        // Cargar referencias desde CSV
        val mapaReferencias: Map<String, String> = cargarReferencias()

        val btnContarTubos: Button = findViewById(R.id.btnContarTubos)
        btnContarTubos.setOnClickListener {
            val drawable = capturedImage.drawable
            if (drawable != null) {
                val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                inferirImagen(bitmap)
            } else {
                Toast.makeText(this, "Primero toma una foto", Toast.LENGTH_SHORT).show()
            }
        }

        // Listener para descripción de material
        inputMaterial.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val ref = s.toString().trim()
                labelDescripcion.text = if (ref.isNotEmpty()) {
                    mapaReferencias[ref] ?: "Referencia no encontrada"
                } else ""
            }
        })

        if (listaCentros.isNotEmpty()) {
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                listaCentros
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCentro.adapter = adapter
        }

        // Botón para tomar foto
        btnTakePhoto.setOnClickListener {
            takePhoto()
        }

        // Pedir permisos de cámara
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 10)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error iniciando cámara", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        imageCapture?.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    var bitmap = imageProxyToBitmap(imageProxy)

                    val rotation = imageProxy.imageInfo.rotationDegrees
                    if (rotation != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotation.toFloat())
                        bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                    }

                    capturedImage.setImageBitmap(bitmap)
                    capturedImage.visibility = ImageView.VISIBLE
                    previewView.visibility = PreviewView.GONE

                    imageProxy.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(applicationContext, "Error al capturar la foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Conversión YUV a Bitmap simple
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun leerCentrosDesdeCSV(): List<String> {
        val lista = mutableListOf<String>()
        try {
            val inputStream = resources.openRawResource(R.raw.centros)
            inputStream.bufferedReader().useLines { lines -> lines.forEach { lista.add(it.trim()) } }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer centros.csv", Toast.LENGTH_SHORT).show()
        }
        return lista
    }

    private fun cargarReferencias(): Map<String, String> {
        val mapa = mutableMapOf<String, String>()
        try {
            val inputStream = resources.openRawResource(R.raw.referencias)
            inputStream.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (index == 0) return@forEachIndexed
                    val columnas = line.split(";")
                    if (columnas.size >= 2) {
                        mapa[columnas[0].trim()] = columnas[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer referencias.csv", Toast.LENGTH_SHORT).show()
        }
        return mapa
    }

    private fun cargarModeloONNX(): ByteArray? {
        return try {
            assets.open("best.onnx").use { it.readBytes() }
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando modelo ONNX", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun inicializarONNX(modeloBytes: ByteArray) {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            ortSession = ortEnvironment!!.createSession(modeloBytes)
            Toast.makeText(this, "Modelo ONNX cargado", Toast.LENGTH_SHORT).show()
        } catch (e: OrtException) {
            Toast.makeText(this, "Error inicializando ONNX Runtime", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun inferirImagen(bitmap: Bitmap) {
        if (ortSession == null || ortEnvironment == null) return

        try {
            val inputName = ortSession!!.inputNames.iterator().next()
            val modelWidth = 640
            val modelHeight = 640

            // Convertir bitmap a FloatBuffer
            val inputBuffer = bitmapToFloatBufferNCHW(bitmap, modelWidth, modelHeight)
            val shape = longArrayOf(1, 3, modelHeight.toLong(), modelWidth.toLong())
            val tensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, shape)

            // Ejecutar inferencia
            val results = ortSession!!.run(mapOf(inputName to tensor))
            val output = results[0] as OnnxTensor

            // La salida real es [1, 5, 8400]
            val rawOutput = (output.value as Array<Array<FloatArray>>)[0] // [5][8400]

            val confidenceThreshold = 0.5f
            val detections = mutableListOf<Detection>()

            // Recorremos las 8400 predicciones
            for (i in 0 until 8400) {
                val x = rawOutput[0][i]
                val y = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]
                val score = rawOutput[4][i]

                if (score > confidenceThreshold) {
                    val x1 = x - w / 2
                    val y1 = y - h / 2
                    val x2 = x + w / 2
                    val y2 = y + h / 2
                    detections.add(Detection(x1, y1, x2, y2, score))
                }
            }

            // Aplicar Non-Maximum Suppression
            val finalDetections = nms(detections, iouThreshold = 0.5f)

            // Dibujar puntos verdes en el centro de cada detección
            val bitmapWithDetections = drawDetectionsOnBitmap(bitmap, finalDetections)

            runOnUiThread {
                capturedImage.setImageBitmap(bitmapWithDetections)
                Toast.makeText(this, "Tubos detectados: ${finalDetections.size}", Toast.LENGTH_LONG).show()
            }

            // ⚠️ No cerrar tensor ni results aquí en Android (puede dar problemas de memoria)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error en inferencia ONNX", Toast.LENGTH_SHORT).show()
        }
    }



    private fun bitmapToFloatBufferNCHW(bitmap: Bitmap, modelWidth: Int, modelHeight: Int): FloatBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, modelWidth, modelHeight, true)
        val floatArray = FloatArray(3 * modelWidth * modelHeight)
        var idx = 0

        for (y in 0 until modelHeight) {
            for (x in 0 until modelWidth) {
                val pixel = resized.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                floatArray[idx++] = r
            }
        }
        for (y in 0 until modelHeight) {
            for (x in 0 until modelWidth) {
                val pixel = resized.getPixel(x, y)
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                floatArray[idx++] = g
            }
        }
        for (y in 0 until modelHeight) {
            for (x in 0 until modelWidth) {
                val pixel = resized.getPixel(x, y)
                val b = (pixel and 0xFF) / 255.0f
                floatArray[idx++] = b
            }
        }

        val buffer = ByteBuffer.allocateDirect(4 * floatArray.size)
        buffer.order(ByteOrder.nativeOrder())
        for (f in floatArray) buffer.putFloat(f)
        buffer.rewind()
        return buffer.asFloatBuffer()
    }

    private fun nms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }.toMutableList()
        val results = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            results.add(current)
            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                val iou = computeIoU(current, other)
                if (iou > iouThreshold) iterator.remove()
            }
        }
        return results
    }

    private fun computeIoU(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.x1, b.x1)
        val y1 = maxOf(a.y1, b.y1)
        val x2 = minOf(a.x2, b.x2)
        val y2 = minOf(a.y2, b.y2)

        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }
    private fun drawDetectionsOnBitmap(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            style = android.graphics.Paint.Style.FILL
            strokeWidth = 8f
        }

        // Escala entre el tamaño del bitmap y el tamaño del modelo
        val scaleX = bitmap.width.toFloat() / 640f
        val scaleY = bitmap.height.toFloat() / 640f

        for (det in detections) {
            val centerX = ((det.x1 + det.x2) / 2) * scaleX
            val centerY = ((det.y1 + det.y2) / 2) * scaleY
            canvas.drawCircle(centerX, centerY, 10f, paint) // puntico verde más visible
        }

        return mutableBitmap
    }


}
