/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.zxing.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.Camera;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.zxing.client.android.camera.CameraConfigurationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */
final class CameraConfigurationManager {

  private static final String TAG = "CameraConfiguration";

  private final Context context;
  private Point screenResolution;
  private Point cameraResolution;
  private int heightOut;

  CameraConfigurationManager(Context context,int heightOut) {
    this.context = context;
    this.heightOut = heightOut;
  }

  /**
   * Reads, one time, values from the camera that are needed by the app.
   */
  void initFromCameraParameters(Camera camera) {
    camera.setDisplayOrientation(90);
    Camera.Parameters parameters = camera.getParameters();
    //parameters.setRotation(90);
    camera.setParameters(parameters);
    parameters = camera.getParameters();
    WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = manager.getDefaultDisplay();
    Point theScreenResolution = new Point();
    display.getSize(theScreenResolution);
    screenResolution = theScreenResolution;
    screenResolution.y -= this.heightOut;
    Log.i(TAG, "Screen resolution: " + screenResolution);
    //cameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution);
    cameraResolution = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), screenResolution.y, screenResolution.x);
    int x = cameraResolution.x;
    //cameraResolution.x=cameraResolution.y;
    //cameraResolution.y=x;
    Log.i(TAG, "Camera resolution: " + cameraResolution);
  }

  void setDesiredCameraParameters(Camera camera, boolean safeMode) {
    Camera.Parameters parameters = camera.getParameters();

    if (parameters == null) {
      Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
      return;
    }

    Log.i(TAG, "Initial camera parameters: " + parameters.flatten());

    if (safeMode) {
      Log.w(TAG, "In camera config safe mode -- most settings will not be honored");
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    initializeTorch(parameters, prefs, safeMode);

    CameraConfigurationUtils.setFocus(
            parameters,
            true,
            true,
        safeMode);

    if (!safeMode) {
      //if (prefs.getBoolean(PreferencesActivity.KEY_INVERT_SCAN, false)) {
      //  CameraConfigurationUtils.setInvertColor(parameters);
      //}

      //if (!prefs.getBoolean(PreferencesActivity.KEY_DISABLE_BARCODE_SCENE_MODE, true)) {
      //  CameraConfigurationUtils.setBarcodeSceneMode(parameters);
      //}

      //if (!prefs.getBoolean(PreferencesActivity.KEY_DISABLE_METERING, true)) {
      //  CameraConfigurationUtils.setVideoStabilization(parameters);
      //  CameraConfigurationUtils.setFocusArea(parameters);
      //  CameraConfigurationUtils.setMetering(parameters);
      //}

    }

    parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);

    Log.i(TAG, "Final camera parameters: " + parameters.flatten());

    camera.setParameters(parameters);

    Camera.Parameters afterParameters = camera.getParameters();
    Camera.Size afterSize = afterParameters.getPreviewSize();
    if (afterSize!= null && (cameraResolution.x != afterSize.width || cameraResolution.y != afterSize.height)) {
      Log.w(TAG, "Camera said it supported preview size " + cameraResolution.x + 'x' + cameraResolution.y +
                 ", but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height);
      cameraResolution.x = afterSize.width;
      cameraResolution.y = afterSize.height;
    }
  }

  Point getCameraResolution() {
    return cameraResolution;
  }

  Point getScreenResolution() {
    return screenResolution;
  }

  boolean getTorchState(Camera camera) {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      if (parameters != null) {
        String flashMode = parameters.getFlashMode();
        return flashMode != null &&
            (Camera.Parameters.FLASH_MODE_ON.equals(flashMode) ||
             Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
      }
    }
    return false;
  }

  void setTorch(Camera camera, boolean newSetting) {
    Camera.Parameters parameters = camera.getParameters();
    doSetTorch(parameters, newSetting, false);
    camera.setParameters(parameters);
  }

  private void initializeTorch(Camera.Parameters parameters, SharedPreferences prefs, boolean safeMode) {
    boolean currentSetting = FrontLightMode.readPref(prefs) == FrontLightMode.ON;
    doSetTorch(parameters, currentSetting, safeMode);
  }

  private void doSetTorch(Camera.Parameters parameters, boolean newSetting, boolean safeMode) {
    CameraConfigurationUtils.setTorch(parameters, newSetting);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    //if (!safeMode && !prefs.getBoolean(PreferencesActivity.KEY_DISABLE_EXPOSURE, true)) {
      CameraConfigurationUtils.setBestExposure(parameters, newSetting);
    //}
  }

  private Point getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
    List<Camera.Size> supportedPreviewSizes = new ArrayList<>(sizes);
    Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
      @Override
      public int compare(Camera.Size a, Camera.Size b) {
        int aPixels = a.height * a.width;
        int bPixels = b.height * b.width;
        if (bPixels < aPixels) {
          return -1;
        }
        if (bPixels > aPixels) {
          return 1;
        }
        return 0;
      }
    });

    final double ASPECT_TOLERANCE = 0.05;

    double targetRatio = (double) w / h;
    if (supportedPreviewSizes == null) return null;

    Camera.Size optimalSize = null;

    for (Camera.Size size : supportedPreviewSizes) {
      double ratio = (double) size.width / size.height;

      if(targetRatio>ratio){
        return new Point(size.width,size.height);
      }
    }

    // Cannot find the one match the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      for (Camera.Size size : supportedPreviewSizes) {
                /*if (Math.abs(size.width - w) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.width - w);
                }*/
        if(size.width<=w &&size.height<=h){
          optimalSize = size;
          return new Point(optimalSize.width,optimalSize.height);
        }
      }
    }
    return new Point(optimalSize.width,optimalSize.height);
  }

}
