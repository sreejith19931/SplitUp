package com.finance.splitup.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import com.finance.splitup.data.ReceiptProcessor
import com.finance.splitup.data.SampleReceipts
import com.finance.splitup.viewmodel.FinanceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

class LiveReceiptAnalyzer(
    private val onBoundsDetected: (android.graphics.RectF?) -> Unit
) : androidx.camera.core.ImageAnalysis.Analyzer {
    private val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTime = 0L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTime < 250) {
            imageProxy.close()
            return
        }
        lastAnalyzedTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotationDegrees)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    if (visionText.textBlocks.isEmpty()) {
                        onBoundsDetected(null)
                    } else {
                        var minX = Float.MAX_VALUE
                        var minY = Float.MAX_VALUE
                        var maxX = Float.MIN_VALUE
                        var maxY = Float.MIN_VALUE

                        val imgWidth = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.height else mediaImage.width
                        val imgHeight = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.width else mediaImage.height

                        for (block in visionText.textBlocks) {
                            block.boundingBox?.let { rect ->
                                if (rect.left < minX) minX = rect.left.toFloat()
                                if (rect.top < minY) minY = rect.top.toFloat()
                                if (rect.right > maxX) maxX = rect.right.toFloat()
                                if (rect.bottom > maxY) maxY = rect.bottom.toFloat()
                            }
                        }

                        if (maxX > minX && maxY > minY && imgWidth > 0 && imgHeight > 0) {
                            val normLeft = (minX / imgWidth).coerceIn(0.02f, 0.9f)
                            val normTop = (minY / imgHeight).coerceIn(0.02f, 0.9f)
                            val normRight = (maxX / imgWidth).coerceIn(normLeft + 0.05f, 0.98f)
                            val normBottom = (maxY / imgHeight).coerceIn(normTop + 0.05f, 0.98f)
                            onBoundsDetected(android.graphics.RectF(normLeft, normTop, normRight, normBottom))
                        } else {
                            onBoundsDetected(null)
                        }
                    }
                    imageProxy.close()
                }
                .addOnFailureListener {
                    onBoundsDetected(null)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReceiptScannerScreen(
    viewModel: FinanceViewModel,
    onReceiptExtracted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    var isProcessing by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveBounds by remember { mutableStateOf<android.graphics.RectF?>(null) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            bindToLifecycle(lifecycleOwner)
        }
    }

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    DisposableEffect(cameraController) {
        cameraController.setImageAnalysisAnalyzer(
            mainExecutor,
            LiveReceiptAnalyzer { bounds ->
                liveBounds = bounds
            }
        )
        onDispose {
            try {
                cameraController.clearImageAnalysisAnalyzer()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(capturedBitmap) {
        if (capturedBitmap != null) {
            try {
                cameraController.unbind()
            } catch (_: Exception) {}
        } else {
            try {
                cameraController.bindToLifecycle(lifecycleOwner)
            } catch (_: Exception) {}
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = decodeSampledBitmapFromUri(context, uri)
                capturedBitmap = bitmap
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraController.enableTorch(false)
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Scanner & OCR", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                actions = {
                    IconButton(onClick = {
                        isTorchOn = !isTorchOn
                        try {
                            cameraController.enableTorch(isTorchOn)
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cameraPermissionState.status.isGranted) {
                if (capturedBitmap == null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                controller = cameraController
                            }
                        }
                    )

                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bounds = liveBounds
                        val left: Float
                        val top: Float
                        val frameWidth: Float
                        val frameHeight: Float
                        val boxColor: Color

                        if (bounds != null) {
                            val padX = (bounds.right - bounds.left) * 0.05f
                            val padY = (bounds.bottom - bounds.top) * 0.05f
                            left = ((bounds.left - padX).coerceAtLeast(0.05f)) * size.width
                            top = ((bounds.top - padY).coerceAtLeast(0.05f)) * size.height
                            val right = ((bounds.right + padX).coerceAtMost(0.95f)) * size.width
                            val bottom = ((bounds.bottom + padY).coerceAtMost(0.95f)) * size.height
                            frameWidth = right - left
                            frameHeight = bottom - top
                            boxColor = Color.Green
                        } else {
                            frameWidth = size.width * 0.82f
                            frameHeight = size.height * 0.58f
                            left = (size.width - frameWidth) / 2f
                            top = (size.height - frameHeight) / 2.3f
                            boxColor = primaryColor
                        }

                        val cornerLen = 32.dp.toPx()
                        val strokeWidth = 4.dp.toPx()

                        if (bounds != null) {
                            drawRect(
                                color = boxColor.copy(alpha = 0.35f),
                                topLeft = Offset(left, top),
                                size = Size(frameWidth, frameHeight),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Draw target alignment brackets
                        drawLine(boxColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
                        drawLine(boxColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)

                        drawLine(boxColor, Offset(left + frameWidth, top), Offset(left + frameWidth - cornerLen, top), strokeWidth)
                        drawLine(boxColor, Offset(left + frameWidth, top), Offset(left + frameWidth, top + cornerLen), strokeWidth)

                        drawLine(boxColor, Offset(left, top + frameHeight), Offset(left + cornerLen, top + frameHeight), strokeWidth)
                        drawLine(boxColor, Offset(left, top + frameHeight), Offset(left, top + frameHeight - cornerLen), strokeWidth)

                        drawLine(boxColor, Offset(left + frameWidth, top + frameHeight), Offset(left + frameWidth - cornerLen, top + frameHeight), strokeWidth)
                        drawLine(boxColor, Offset(left + frameWidth, top + frameHeight), Offset(left + frameWidth, top + frameHeight - cornerLen), strokeWidth)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Align receipt within frame & capture",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Gallery", tint = MaterialTheme.colorScheme.primary)
                                }
                                Text("Gallery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.currentExtractedReceipt = SampleReceipts.sampleWholeFoods
                                            onReceiptExtracted()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Sample Receipt", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                                Text("Sample", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = {
                                        isProcessing = true
                                        val executor = ContextCompat.getMainExecutor(context)
                                        cameraController.takePicture(
                                            executor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val buffer = image.planes[0].buffer
                                                    val bytes = ByteArray(buffer.remaining())
                                                    buffer.get(bytes)
                                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                    val rotationDegrees = image.imageInfo.rotationDegrees
                                                    val rotatedBitmap = if (rotationDegrees != 0) {
                                                        val matrix = android.graphics.Matrix().apply {
                                                            postRotate(rotationDegrees.toFloat())
                                                        }
                                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                                    } else {
                                                        bitmap
                                                    }
                                                    image.close()
                                                    isProcessing = false
                                                    capturedBitmap = rotatedBitmap
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    isProcessing = false
                                                    coroutineScope.launch {
                                                        viewModel.currentExtractedReceipt = SampleReceipts.sampleWholeFoods
                                                        onReceiptExtracted()
                                                    }
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(34.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SplitUp needs camera access to scan physical receipts for automatic line-item OCR extraction.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Grant Camera Permission")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.currentExtractedReceipt = SampleReceipts.sampleWholeFoods
                            onReceiptExtracted()
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Use Sample Receipt Instead")
                    }
                }
            }

            if (capturedBitmap != null) {
                ReceiptInteractiveCropView(
                    bitmap = capturedBitmap!!,
                    onCropConfirmed = { croppedBmp ->
                        capturedBitmap = null
                        isProcessing = true
                        coroutineScope.launch {
                            viewModel.saveBitmapToInternalStorage(croppedBmp)
                            val extracted = ReceiptProcessor.processReceiptImage(croppedBmp)
                            viewModel.currentExtractedReceipt = extracted
                            isProcessing = false
                            onReceiptExtracted()
                        }
                    },
                    onRetake = { capturedBitmap = null }
                )
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.size(240.dp)
                    ) {
                        com.finance.splitup.ui.components.SplitUpLoadingAnimation(
                            message = "Running OCR & Parsing..."
                        )
                    }
                }
            }
        }
    }
}

enum class CropMode {
    TopLeft, TopRight, BottomLeft, BottomRight, Inside, None
}

@Composable
fun ReceiptInteractiveCropView(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onRetake: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var rotatedBitmap by remember { mutableStateOf(bitmap) }
    var cropLeft by remember(rotatedBitmap) { mutableStateOf(0.05f) }
    var cropTop by remember(rotatedBitmap) { mutableStateOf(0.05f) }
    var cropRight by remember(rotatedBitmap) { mutableStateOf(0.95f) }
    var cropBottom by remember(rotatedBitmap) { mutableStateOf(0.95f) }
    var activeMode by remember { mutableStateOf(CropMode.None) }

    fun performAutoCrop() {
        coroutineScope.launch {
            val detected = ReceiptProcessor.detectReceiptBoundsInBitmap(rotatedBitmap)
            if (detected != null) {
                cropLeft = detected.left
                cropTop = detected.top
                cropRight = detected.right
                cropBottom = detected.bottom
            } else {
                cropLeft = 0.08f
                cropTop = 0.06f
                cropRight = 0.92f
                cropBottom = 0.92f
            }
        }
    }

    LaunchedEffect(rotatedBitmap) {
        performAutoCrop()
    }

    fun autoStraighten() {
        val matrix = android.graphics.Matrix().apply {
            postRotate(90f)
        }
        rotatedBitmap = Bitmap.createBitmap(rotatedBitmap, 0, 0, rotatedBitmap.width, rotatedBitmap.height, matrix, true)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crop & Straighten Receipt", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { autoStraighten() }) {
                        Text("Rotate 90°", fontSize = 12.sp)
                    }
                    TextButton(onClick = { performAutoCrop() }) {
                        Text("Auto-Crop", fontSize = 12.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = remember(rotatedBitmap) { rotatedBitmap.asImageBitmap() }

                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Captured Receipt",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(rotatedBitmap) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val wImg = rotatedBitmap.width.toFloat()
                                    val hImg = rotatedBitmap.height.toFloat()
                                    val scale = minOf(size.width / wImg, size.height / hImg)
                                    val wDisp = wImg * scale
                                    val hDisp = hImg * scale
                                    val xOff = (size.width - wDisp) / 2f
                                    val yOff = (size.height - hDisp) / 2f

                                    val leftPx = xOff + cropLeft * wDisp
                                    val topPx = yOff + cropTop * hDisp
                                    val rightPx = xOff + cropRight * wDisp
                                    val bottomPx = yOff + cropBottom * hDisp

                                    val hitRadius = 90f
                                    activeMode = when {
                                        (offset - Offset(leftPx, topPx)).getDistance() < hitRadius -> CropMode.TopLeft
                                        (offset - Offset(rightPx, topPx)).getDistance() < hitRadius -> CropMode.TopRight
                                        (offset - Offset(leftPx, bottomPx)).getDistance() < hitRadius -> CropMode.BottomLeft
                                        (offset - Offset(rightPx, bottomPx)).getDistance() < hitRadius -> CropMode.BottomRight
                                        offset.x in leftPx..rightPx && offset.y in topPx..bottomPx -> CropMode.Inside
                                        else -> CropMode.Inside
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val wImg = rotatedBitmap.width.toFloat()
                                    val hImg = rotatedBitmap.height.toFloat()
                                    val scale = minOf(size.width / wImg, size.height / hImg)
                                    val wDisp = (wImg * scale).coerceAtLeast(1f)
                                    val hDisp = (hImg * scale).coerceAtLeast(1f)

                                    val deltaX = dragAmount.x / wDisp
                                    val deltaY = dragAmount.y / hDisp

                                    when (activeMode) {
                                        CropMode.TopLeft -> {
                                            cropLeft = (cropLeft + deltaX).coerceIn(0f, cropRight - 0.1f)
                                            cropTop = (cropTop + deltaY).coerceIn(0f, cropBottom - 0.1f)
                                        }
                                        CropMode.TopRight -> {
                                            cropRight = (cropRight + deltaX).coerceIn(cropLeft + 0.1f, 1f)
                                            cropTop = (cropTop + deltaY).coerceIn(0f, cropBottom - 0.1f)
                                        }
                                        CropMode.BottomLeft -> {
                                            cropLeft = (cropLeft + deltaX).coerceIn(0f, cropRight - 0.1f)
                                            cropBottom = (cropBottom + deltaY).coerceIn(cropTop + 0.1f, 1f)
                                        }
                                        CropMode.BottomRight -> {
                                            cropRight = (cropRight + deltaX).coerceIn(cropLeft + 0.1f, 1f)
                                            cropBottom = (cropBottom + deltaY).coerceIn(cropTop + 0.1f, 1f)
                                        }
                                        CropMode.Inside -> {
                                            val width = cropRight - cropLeft
                                            val height = cropBottom - cropTop
                                            cropLeft = (cropLeft + deltaX).coerceIn(0f, 1f - width)
                                            cropTop = (cropTop + deltaY).coerceIn(0f, 1f - height)
                                            cropRight = cropLeft + width
                                            cropBottom = cropTop + height
                                        }
                                        CropMode.None -> {}
                                    }
                                }
                            )
                        }
                ) {
                    val wImg = rotatedBitmap.width.toFloat()
                    val hImg = rotatedBitmap.height.toFloat()
                    val scale = minOf(size.width / wImg, size.height / hImg)
                    val wDisp = wImg * scale
                    val hDisp = hImg * scale
                    val xOff = (size.width - wDisp) / 2f
                    val yOff = (size.height - hDisp) / 2f

                    val leftPx = xOff + cropLeft * wDisp
                    val topPx = yOff + cropTop * hDisp
                    val rightPx = xOff + cropRight * wDisp
                    val bottomPx = yOff + cropBottom * hDisp

                    val overlayColor = Color.Black.copy(alpha = 0.6f)
                    // Top overlay
                    drawRect(color = overlayColor, topLeft = Offset.Zero, size = Size(size.width, topPx))
                    // Bottom overlay
                    drawRect(color = overlayColor, topLeft = Offset(0f, bottomPx), size = Size(size.width, size.height - bottomPx))
                    // Left overlay
                    drawRect(color = overlayColor, topLeft = Offset(0f, topPx), size = Size(leftPx, bottomPx - topPx))
                    // Right overlay
                    drawRect(color = overlayColor, topLeft = Offset(rightPx, topPx), size = Size(size.width - rightPx, bottomPx - topPx))

                    // Green crop border
                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(rightPx - leftPx, bottomPx - topPx),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Right-angled L-bracket corner handles
                    val cornerArm = 26.dp.toPx()
                    val cornerStroke = 5.dp.toPx()
                    val handleColor = Color.White

                    // Top-Left corner ┌
                    drawLine(handleColor, Offset(leftPx - cornerStroke / 2, topPx), Offset(leftPx + cornerArm, topPx), cornerStroke)
                    drawLine(handleColor, Offset(leftPx, topPx - cornerStroke / 2), Offset(leftPx, topPx + cornerArm), cornerStroke)

                    // Top-Right corner ┐
                    drawLine(handleColor, Offset(rightPx + cornerStroke / 2, topPx), Offset(rightPx - cornerArm, topPx), cornerStroke)
                    drawLine(handleColor, Offset(rightPx, topPx - cornerStroke / 2), Offset(rightPx, topPx + cornerArm), cornerStroke)

                    // Bottom-Left corner └
                    drawLine(handleColor, Offset(leftPx - cornerStroke / 2, bottomPx), Offset(leftPx + cornerArm, bottomPx), cornerStroke)
                    drawLine(handleColor, Offset(leftPx, bottomPx + cornerStroke / 2), Offset(leftPx, bottomPx - cornerArm), cornerStroke)

                    // Bottom-Right corner ┘
                    drawLine(handleColor, Offset(rightPx + cornerStroke / 2, bottomPx), Offset(rightPx - cornerArm, bottomPx), cornerStroke)
                    drawLine(handleColor, Offset(rightPx, bottomPx + cornerStroke / 2), Offset(rightPx, bottomPx - cornerArm), cornerStroke)
                }
            }

            Text(
                text = "Drag corner handles or inside the box to adjust crop area.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Retake")
                }

                Button(
                    onClick = {
                        try {
                            val startX = (rotatedBitmap.width * cropLeft).toInt().coerceIn(0, rotatedBitmap.width - 1)
                            val startY = (rotatedBitmap.height * cropTop).toInt().coerceIn(0, rotatedBitmap.height - 1)
                            val width = (rotatedBitmap.width * (cropRight - cropLeft)).toInt().coerceIn(1, rotatedBitmap.width - startX)
                            val height = (rotatedBitmap.height * (cropBottom - cropTop)).toInt().coerceIn(1, rotatedBitmap.height - startY)
                            val cropped = Bitmap.createBitmap(rotatedBitmap, startX, startY, width, height)
                            onCropConfirmed(cropped)
                        } catch (_: Exception) {
                            onCropConfirmed(rotatedBitmap)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crop & Run OCR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun decodeSampledBitmapFromUri(context: android.content.Context, uri: Uri, reqWidth: Int = 1024, reqHeight: Int = 1024): Bitmap? {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
                inJustDecodeBounds = false
            }
            return BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (_: Exception) {}
    return null
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
