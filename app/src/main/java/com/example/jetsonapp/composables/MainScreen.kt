package com.example.jetsonapp.composables


import android.Manifest
import android.net.Uri
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsonapp.JetsonViewModel
import com.example.jetsonapp.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import coil3.compose.AsyncImage

@OptIn(ExperimentalComposeUiApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(jetsonViewModel: JetsonViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf("".toUri()) }
    val jetsonIsWorking by jetsonViewModel.jetsonIsWorking.collectAsStateWithLifecycle()
    val microphoneIsRecording by jetsonViewModel.microphoneIsRecording.collectAsStateWithLifecycle()

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

    // https://developer.android.com/training/data-storage/shared/documents-files
    val imagePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                jetsonViewModel.updateSelectedImage(context, uri)
                imageUri = uri
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {

        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            ImageFromUri(imageUri)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier
            .height(48.dp)
            .align(Alignment.CenterHorizontally)) {
            if (jetsonIsWorking) {
                CircularProgressIndicator(
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
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
            }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Send Icon"
                    )
                    Text(
                        text = "Image",
                        color = Color.Black,
                        fontSize = 24.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Image(
                painter = painterResource(id = R.drawable.baseline_mic_24),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .align(Alignment.CenterVertically)
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
                    if (microphoneIsRecording) colorResource(id = R.color.teal_200) else colorResource(
                        id = R.color.black
                    )
                )
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Select an image then hold, speak and release",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun ImageFromUri(uri: Uri?) {
    if (uri != null) {
        AsyncImage(
            model = uri,
            contentDescription = "Loaded image",
            placeholder = painterResource(R.drawable.image_icon),
            error = painterResource(R.drawable.image_icon),
            modifier = Modifier.size(320.dp),
        )
    }
}
