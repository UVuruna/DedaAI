/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.GeminiSessionViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    isPhoneMode: Boolean = false,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        ),
    geminiViewModel: GeminiSessionViewModel = viewModel(),
) {
    val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()
    val geminiUiState by geminiViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Wire Gemini VM to Stream VM for frame forwarding
    LaunchedEffect(geminiViewModel) {
        streamViewModel.geminiViewModel = geminiViewModel
    }

    // Start stream or phone camera
    LaunchedEffect(isPhoneMode) {
        if (isPhoneMode) {
            geminiViewModel.streamingMode = StreamingMode.PHONE
            streamViewModel.startPhoneCamera(lifecycleOwner)
        } else {
            geminiViewModel.streamingMode = StreamingMode.GLASSES
            streamViewModel.startStream()
        }
    }

    // Clean up on exit
    DisposableEffect(Unit) {
        onDispose {
            if (geminiUiState.isGeminiActive) {
                geminiViewModel.stopSession()
            }
        }
    }

    // Show errors as toasts
    LaunchedEffect(geminiUiState.errorMessage) {
        geminiUiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            geminiViewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Video feed
        streamUiState.videoFrame?.let { videoFrame ->
            Image(
                bitmap = videoFrame.asImageBitmap(),
                contentDescription = stringResource(R.string.live_stream),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Overlays + controls
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Top overlays (below status bar)
            Column(modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp)) {
                // Gemini overlay
                if (geminiUiState.isGeminiActive) {
                    GeminiOverlay(uiState = geminiUiState)
                }
            }

            // Controls at bottom
            ControlsRow(
                onStopStream = {
                    if (geminiUiState.isGeminiActive) geminiViewModel.stopSession()
                    streamViewModel.stopStream()
                    wearablesViewModel.navigateToDeviceSelection()
                },
                onCapturePhoto = { streamViewModel.capturePhoto() },
                onToggleAI = {
                    if (geminiUiState.isGeminiActive) {
                        geminiViewModel.stopSession()
                    } else {
                        geminiViewModel.startSession()
                    }
                },
                isAIActive = geminiUiState.isGeminiActive,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // Share photo dialog
    streamUiState.capturedPhoto?.let { photo ->
        if (streamUiState.isShareDialogVisible) {
            SharePhotoDialog(
                photo = photo,
                onDismiss = { streamViewModel.hideShareDialog() },
                onShare = { bitmap ->
                    streamViewModel.sharePhoto(bitmap)
                    streamViewModel.hideShareDialog()
                },
            )
        }
    }
}
