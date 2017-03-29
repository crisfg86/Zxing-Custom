package com.google.zxing.client.zxing;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;

import com.google.zxing.Result;
import com.google.zxing.client.zxing.camera.CameraManager;

/**
 * Created by husky on 16/05/15.
 */
public interface CaptureActivityInterface {
    ViewfinderView getViewfinderView();

    void handleDecode(Result obj, Bitmap barcode, float scaleFactor);

    Activity getActivity();

    CameraManager getCameraManager();

    Handler getHandler();

    void drawViewfinder();
}
