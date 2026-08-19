/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.GeminiSessionViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.phone.PhoneCameraManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.PhotoDecode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.VideoFrames
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StreamViewModel(
    application: Application,
    private val wearablesViewModel: WearablesViewModel,
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "StreamViewModel"
    private val INITIAL_STATE = StreamUiState()
  }

  private val deviceSelector: DeviceSelector = wearablesViewModel.deviceSelector
  private var streamSession: StreamSession? = null

  private val _uiState = MutableStateFlow(INITIAL_STATE)
  val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

  private var videoJob: Job? = null
  private var stateJob: Job? = null

  // DedaAI additions
  var geminiViewModel: GeminiSessionViewModel? = null
  private var phoneCameraManager: PhoneCameraManager? = null

  fun startStream() {
    videoJob?.cancel()
    stateJob?.cancel()

    // Start foreground service to keep streaming alive in background / screen locked
    StreamingService.start(getApplication(), "stream-screen")

    val streamSession =
        Wearables.startStreamSession(
                getApplication(),
                deviceSelector,
                StreamConfiguration(videoQuality = VideoQuality.HIGH, 24),
            )
            .also { streamSession = it }
    _uiState.update { it.copy(streamingMode = StreamingMode.GLASSES) }
    videoJob = viewModelScope.launch { streamSession.videoStream.collect { handleVideoFrame(it) } }
    stateJob =
        viewModelScope.launch {
          streamSession.state.collect { currentState ->
            val prevState = _uiState.value.streamSessionState
            _uiState.update { it.copy(streamSessionState = currentState) }

            // navigate back when state transitioned to STOPPED
            if (currentState != prevState && currentState == StreamSessionState.STOPPED) {
              stopStream()
              wearablesViewModel.navigateToDeviceSelection()
            }
          }
        }
  }

  fun startPhoneCamera(lifecycleOwner: LifecycleOwner) {
    val manager = PhoneCameraManager(getApplication())
    phoneCameraManager = manager

    manager.onFrameCaptured = { bitmap ->
      _uiState.update { it.copy(videoFrame = bitmap) }
      // Forward to Gemini (throttled inside the VM)
      geminiViewModel?.onCameraFrame(bitmap)
    }

    _uiState.update {
      it.copy(
        streamingMode = StreamingMode.PHONE,
        streamSessionState = StreamSessionState.STREAMING,
      )
    }
    manager.start(lifecycleOwner)
    Log.d(TAG, "Phone camera mode started")
  }

  fun stopStream() {
    // Stop foreground service
    StreamingService.stop(getApplication(), "stream-screen")

    videoJob?.cancel()
    videoJob = null
    stateJob?.cancel()
    stateJob = null
    streamSession?.close()
    streamSession = null
    phoneCameraManager?.stop()
    phoneCameraManager = null
    _uiState.update { INITIAL_STATE }
  }

  fun capturePhoto() {
    if (uiState.value.isCapturing) {
      Log.d(TAG, "Photo capture already in progress, ignoring request")
      return
    }

    if (uiState.value.streamSessionState == StreamSessionState.STREAMING) {
      // Phone mode: capture current video frame as photo
      if (uiState.value.streamingMode == StreamingMode.PHONE) {
        uiState.value.videoFrame?.let { frame ->
          _uiState.update { it.copy(capturedPhoto = frame, isShareDialogVisible = true) }
        }
        return
      }

      Log.d(TAG, "Starting photo capture")
      _uiState.update { it.copy(isCapturing = true) }

      viewModelScope.launch {
        streamSession
            ?.capturePhoto()
            ?.onSuccess { photoData ->
              Log.d(TAG, "Photo capture successful")
              handlePhotoData(photoData)
              _uiState.update { it.copy(isCapturing = false) }
            }
            ?.onFailure {
              Log.e(TAG, "Photo capture failed")
              _uiState.update { it.copy(isCapturing = false) }
            }
      }
    } else {
      Log.w(
          TAG,
          "Cannot capture photo: stream not active (state=${uiState.value.streamSessionState})",
      )
    }
  }

  fun showShareDialog() {
    _uiState.update { it.copy(isShareDialogVisible = true) }
  }

  fun hideShareDialog() {
    _uiState.update { it.copy(isShareDialogVisible = false) }
  }

  fun sharePhoto(bitmap: Bitmap) {
    val context = getApplication<Application>()
    val imagesFolder = File(context.cacheDir, "images")
    try {
      imagesFolder.mkdirs()
      val file = File(imagesFolder, "shared_image.png")
      FileOutputStream(file).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
      }

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_SEND)
      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      intent.putExtra(Intent.EXTRA_STREAM, uri)
      intent.type = "image/png"
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

      val chooser = Intent.createChooser(intent, "Share Image")
      chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      context.startActivity(chooser)
    } catch (e: IOException) {
      Log.e("StreamViewModel", "Failed to share photo", e)
    }
  }

  private fun handleVideoFrame(videoFrame: VideoFrame) {
    // Conversion lives in VideoFrames so the Deda conversation (TalkVision)
    // turns frames into bitmaps exactly the same way.
    val bitmap = VideoFrames.toBitmap(videoFrame) ?: return
    _uiState.update { it.copy(videoFrame = bitmap) }

    // Forward to Gemini (throttled inside the VM)
    geminiViewModel?.onCameraFrame(bitmap)
  }

  private fun handlePhotoData(photo: PhotoData) {
    // Decoding lives in PhotoDecode so a Deda conversation's one-shot picture
    // (TalkVision) turns stills into bitmaps exactly the same way.
    val capturedPhoto = PhotoDecode.toBitmap(photo)
    if (capturedPhoto == null) {
      Log.e(TAG, "Failed to decode captured photo")
      return
    }
    _uiState.update { it.copy(capturedPhoto = capturedPhoto, isShareDialogVisible = true) }
  }

  override fun onCleared() {
    super.onCleared()
    stopStream()
    stateJob?.cancel()
  }

  class Factory(
      private val application: Application,
      private val wearablesViewModel: WearablesViewModel,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(StreamViewModel::class.java)) {
        @Suppress("UNCHECKED_CAST", "KotlinGenericsCast")
        return StreamViewModel(
            application = application,
            wearablesViewModel = wearablesViewModel,
        )
            as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}
