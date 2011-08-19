/*
 * Copyright (C) 2011 Google Inc. All Rights Reserved.
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

package net.pandorica.opencv.pano;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * Implements the surface view used to be used by the camera.
 * Listens for on screen clicks and takes pictures.
 */
public abstract class PanoSurfaceBase extends SurfaceView implements SurfaceHolder.Callback,
       Runnable, OnClickListener {
    private Camera              mCamera;
    private SurfaceHolder       mHolder;
    private int                 mFrameWidth;
    private int                 mFrameHeight;
    private byte[]              mFrame;
    private boolean             mThreadRun;
    private boolean             mCapturing;
    private PanoCamera          mPanoClass;

    /**
     * Constructs local variables. PanoCamera is needed to for take Picture callback
     * @param context
     * @param cls
     */
    public PanoSurfaceBase(Context context, PanoCamera cls) {
        super(context);
        mPanoClass = cls;
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    public int getFrameWidth() {
        return mFrameWidth;
    }

    public int getFrameHeight() {
        return mFrameHeight;
    }

    /**
     * Callback for when this surface is changed. Updates preview size starts previewing.
     */
    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            mFrameWidth = width;
            mFrameHeight = height;

            // selecting optimal camera preview size
            {
                double minDiff = Double.MAX_VALUE;
                for (Camera.Size size : sizes) {
                    if (Math.abs(size.height - height) < minDiff) {
                        mFrameWidth = size.width;
                        mFrameHeight = size.height;
                        minDiff = Math.abs(size.height - height);
                    }
                }
            }
            params.setPreviewSize(getFrameWidth(), getFrameHeight());
            mCamera.setParameters(params);
            mCamera.startPreview();
        }
    }

    /**
     * Prepares surface and camera for previewing
     */
    public void surfaceCreated(SurfaceHolder holder) {
        this.setOnClickListener(this);
        mCapturing = false;
        mCamera = Camera.open();
        mCamera.setPreviewCallback(new PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {
                synchronized (PanoSurfaceBase.this) {
                    mFrame = data;
                    PanoSurfaceBase.this.notify();
                }
            }
        });
        (new Thread(this)).start();
    }

    /**
     * Cleanup surface and camera
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        mThreadRun = false;
        if (mCamera != null) {
            synchronized (this) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.release();
                mCamera = null;
            }
        }
    }

    protected abstract Bitmap processFrame(byte[] data);

    /**
     * Draws the generated bitmap to the canvas
     */
    public void run() {
        mThreadRun = true;
        while (mThreadRun) {
            Bitmap bmp = null;

            synchronized (this) {
                try {
                    this.wait();
                    bmp = processFrame(mFrame);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (bmp != null) {
                Canvas canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bmp, (canvas.getWidth() - getFrameWidth()) / 2,
                                      (canvas.getHeight() - getFrameHeight()) / 2, null);
                    mHolder.unlockCanvasAndPost(canvas);
                }
                bmp.recycle();
            }
        }
    }

    /**
     * Passes jpeg data to the Camera Activity for saving
     */
    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {

        public void onPictureTaken(byte[] data, Camera camera) {
            mPanoClass.new SavePhotoTask().execute(data);
        }
    };

    /**
     * Listener for on screen clicks.
     * Workaround for bug in 2.3.X: we stop the preview and start a new one against a null surface
     * Then take the picture.
     */
    public void onClick(View v) {
        if ((mCamera != null) && (!mCapturing)) {
            mCapturing = true;
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            try {
                mCamera.setPreviewDisplay(null);
                mCamera.startPreview();
                mCamera.takePicture(null, null, jpegCallback);
            } catch (IOException e) {
                // Camera is unavailable, don't take a picture
                // TODO: alert user
            }

        }
    }
}
