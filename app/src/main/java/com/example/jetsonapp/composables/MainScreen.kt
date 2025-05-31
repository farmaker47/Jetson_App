package com.example.jetsonapp.composables


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsonapp.JetsonViewModel
import com.example.jetsonapp.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import coil3.compose.AsyncImage
import com.example.jetsonapp.utils.CameraUtil.checkFrontCamera
import com.example.jetsonapp.utils.CameraUtil.createTempPictureUri
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.delay

@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun MainScreen(jetsonViewModel: JetsonViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf("".toUri()) }
    var capturedBitmap by remember {
        mutableStateOf(
            createBitmap(1, 1)
        )
    }
    val jetsonIsWorking by jetsonViewModel.jetsonIsWorking.collectAsStateWithLifecycle()
    val microphoneIsRecording by jetsonViewModel.microphoneIsRecording.collectAsStateWithLifecycle()
    val cameraFunctionTriggered by jetsonViewModel.cameraFunctionTriggered.collectAsStateWithLifecycle()
    val phoneGalleryTriggered by jetsonViewModel.phoneGalleryTriggered.collectAsStateWithLifecycle()
    val userPrompt by jetsonViewModel.userPrompt.collectAsStateWithLifecycle()
    val vlmResult by jetsonViewModel.vlmResult.collectAsStateWithLifecycle()
    var showCameraCaptureBottomSheet by remember { mutableStateOf(false) }
    val cameraCaptureSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tempPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isImageSaved ->
            if (isImageSaved) {
                Log.v("image_", "isImageSaved")
                tempPhotoUri?.let {
                    jetsonViewModel.updateSelectedImage(context, tempPhotoUri)
                    imageUri = tempPhotoUri
                }

                /*handleImageSelected(
                    context = context,
                    uri = tempPhotoUri,
                    onImageSelected = onImageSelected,
                    rotateForPortrait = true,
                )*/
            }
        }

    val takePicturePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted ->
        if (permissionGranted) {
            tempPhotoUri = context.createTempPictureUri()

            //cameraLauncher.launch(tempPhotoUri)

            showCameraCaptureBottomSheet = true
        }
    }

    // https://developer.android.com/training/data-storage/shared/documents-files
    val imagePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                scope.launch {
                    delay(1500)
                    jetsonViewModel.updateSelectedImage(context, uri)
                    imageUri = uri
                    jetsonViewModel.updatePhoneGalleryTriggered(false)
                }
            }
        }

    LaunchedEffect(phoneGalleryTriggered) {
        if (phoneGalleryTriggered) {
            imagePickerLauncher.launch(
                arrayOf(
                    "image/*"
                    // "application/pdf"
                    // "text/plain",
                    // "application/msword",  // .doc
                    // "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    // // .docx
                )
            )
        }
    }

    LaunchedEffect(cameraFunctionTriggered) {
        // Check permission
        if (cameraFunctionTriggered) {
            when (PackageManager.PERMISSION_GRANTED) {
                // Already got permission. Call the lambda.
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) -> {
                    tempPhotoUri = context.createTempPictureUri()

                    // cameraLauncher.launch(tempPhotoUri)

                    showCameraCaptureBottomSheet = true
                }

                // Otherwise, ask for permission
                else -> {
                    takePicturePermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    var hasFrontCamera by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkFrontCamera(context = context, callback = { hasFrontCamera = it })
    }

    // Check for permissions.
    val permissionAudio = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    )
    val launcherAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissionGranted ->
        if (permissionGranted) {
            jetsonViewModel.stopRecordingWav()
            jetsonViewModel.updateMicrophoneIsRecording(false)
        } else {
            if (permissionAudio.status.shouldShowRationale) {
                // Show a rationale if needed
                // showAudioRationalDialog.value = true
                Toast.makeText(
                    context,
                    "You have to grant access to use the microphone",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // showAudioRationalDialog.value = true
                Toast.makeText(
                    context,
                    "You have to grant access to use the microphone",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp, bottom = 48.dp, start = 0.dp, end = 0.dp)
            .background(Color.LightGray)
            .border(BorderStroke(2.dp, Color.Black))
    ) {
        // Top content section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                ImageFromUri(imageUri, capturedBitmap)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                if (jetsonIsWorking) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Loading the models",
                            color = Color.Black,
                            fontSize = 24.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            color = Color.Black
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(42.dp))
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        text = vlmResult,
                        color = Color.Black,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Microphone row at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            // Stop button at the far left with size slightly smaller than the row height
            Image(
                painter = painterResource(id = R.drawable.baseline_stop_circle_24),
                contentDescription = "stop generating",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { jetsonViewModel.stopGenerating() },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colorResource(id = R.color.black))
            )

            // Center text taking available space
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                text = userPrompt,
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            // Microphone icon at the far right with size slightly smaller than the row height
            Image(
                painter = painterResource(id = R.drawable.baseline_mic_24),
                contentDescription = "hold the microphone and speak",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { /* Do something */ }
                    .pointerInteropFilter {
                        when (it.action) {
                            MotionEvent.ACTION_UP -> {
                                launcherAudio.launch(Manifest.permission.RECORD_AUDIO)
                            }

                            else -> {
                                jetsonViewModel.startRecordingWav()
                                jetsonViewModel.updateMicrophoneIsRecording(true)
                            }
                        }
                        true
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(
                    if (microphoneIsRecording) colorResource(id = R.color.teal_200)
                    else colorResource(id = R.color.black)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }

    if (showCameraCaptureBottomSheet) {
        ModalBottomSheet(
            sheetState = cameraCaptureSheetState,
            onDismissRequest = { showCameraCaptureBottomSheet = false }) {

            val lifecycleOwner = LocalLifecycleOwner.current
            val previewUseCase = remember { androidx.camera.core.Preview.Builder().build() }
            val imageCaptureUseCase = remember {
                // Try to limit the image size.
                val preferredSize = Size(512, 512)
                val resolutionStrategy = ResolutionStrategy(
                    preferredSize,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(resolutionStrategy)
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                ImageCapture.Builder().setResolutionSelector(resolutionSelector).build()
            }
            var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
            var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
            val localContext = LocalContext.current
            var cameraSide by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
            val executor = remember { Executors.newSingleThreadExecutor() }

            fun rebindCameraProvider() {
                cameraProvider?.let { cameraProvider ->
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(cameraSide)
                        .build()
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner = lifecycleOwner,
                            cameraSelector = cameraSelector,
                            previewUseCase,
                            imageCaptureUseCase
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.d("MainScreen", "Failed to bind camera", e)
                    }
                }
            }

            LaunchedEffect(Unit) {
                cameraProvider = ProcessCameraProvider.awaitInstance(localContext)
                rebindCameraProvider()
            }

            LaunchedEffect(cameraSide) {
                rebindCameraProvider()
            }

            DisposableEffect(Unit) { // Or key on lifecycleOwner if it makes more sense
                onDispose {
                    cameraProvider?.unbindAll() // Unbind all use cases from the camera provider
                    if (!executor.isShutdown) {
                        executor.shutdown()     // Shut down the executor service
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // PreviewView for the camera feed.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).also {
                            previewUseCase.surfaceProvider = it.surfaceProvider
                            rebindCameraProvider()
                        }
                    },
                )

                // Close button.
                IconButton(
                    onClick = {
                        scope.launch {
                            cameraCaptureSheetState.hide()
                            showCameraCaptureBottomSheet = false
                            jetsonViewModel.updateCameraFunctionTriggered(false)
                        }
                    }, colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ), modifier = Modifier
                        .offset(x = (-8).dp, y = 8.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Button that triggers the image capture process
                IconButton(
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .size(64.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
                    onClick = {
                        val callback = object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    var bitmap = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees
                                    bitmap = if (rotation != 0) {
                                        val matrix = Matrix().apply {
                                            postRotate(rotation.toFloat())
                                        }
                                        Log.d(
                                            "MainScreen",
                                            "image size: ${bitmap.width}, ${bitmap.height}"
                                        )
                                        Bitmap.createBitmap(
                                            bitmap,
                                            0,
                                            0,
                                            bitmap.width,
                                            bitmap.height,
                                            matrix,
                                            true
                                        )
                                    } else bitmap

                                    imageUri = "".toUri()
                                    capturedBitmap = bitmap
                                    scope.launch {
                                        delay(1500)
                                        jetsonViewModel.convertBitmapToBase64(bitmap)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainScreen", "Failed to process image", e)
                                } finally {
                                    image.close()
                                    scope.launch {
                                        cameraCaptureSheetState.hide()
                                        showCameraCaptureBottomSheet = false
                                        jetsonViewModel.updateCameraFunctionTriggered(false)
                                    }
                                }
                            }
                        }
                        imageCaptureUseCase.takePicture(executor, callback)
                    },
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Button that toggles the front and back camera.
                if (hasFrontCamera) {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 40.dp, end = 32.dp)
                            .size(48.dp),
                        onClick = {
                            cameraSide = when (cameraSide) {
                                CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
                                else -> CameraSelector.LENS_FACING_BACK
                            }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.FlipCameraAndroid,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageFromUri(uri: Uri?, bitmap: Bitmap) {
    if (uri != null && uri != "".toUri() || bitmap.width == 1) {
        // Log.v("image_uri", uri.toString())
        AsyncImage(
            model = uri,
            contentDescription = "Loaded image",
            placeholder = painterResource(R.drawable.image_icon),
            error = painterResource(R.drawable.image_icon),
            modifier = Modifier.size(320.dp).clip(RoundedCornerShape(16.dp))
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Loaded image",
            modifier = Modifier.size(320.dp).clip(RoundedCornerShape(16.dp))
        )
    }
}
