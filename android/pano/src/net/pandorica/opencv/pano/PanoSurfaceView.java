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

import org.opencv.android;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.SurfaceHolder;

/**
 * Does frame by frame computations. Currently only converts from YUV to RGB
 */
class PanoSurfaceView extends PanoSurfaceBase {
    private Mat mYuv;
    private Mat mRgba;
    private Mat mGraySubmat;
    private Mat mIntermediateMat;
    private Mat mComparisonMat;

    public PanoSurfaceView(Context context, PanoCamera cls) {
        super(context, cls);
    }

    /**
     * Builds the Mats used to process the data (pre mat to bitmap)
     */
    @Override
    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        super.surfaceChanged(_holder, format, width, height);

        synchronized (this) {
            // initialize Mats before usage
            mYuv = new Mat(getFrameHeight() + getFrameHeight() / 2, getFrameWidth(), CvType.CV_8UC1);
            mGraySubmat = mYuv.submat(0, getFrameHeight(), 0, getFrameWidth());

            mRgba = new Mat();
            mIntermediateMat = new Mat();
            mComparisonMat = new Mat();
        }
    }

    /**
     * Processes each preview frame; currently only converts it to RGB
     */
    @Override
    protected Bitmap processFrame(byte[] data) {
        mYuv.put(0, 0, data);
        Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420i2RGB, 4);

        /*
            Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420i2RGB, 4);
            mComparisonMat = Highgui.imread("/mnt/sdcard/DCIM/Camera/IMG_20110727_173249.jpg");
            FindFeatures(mGraySubmat.getNativeObjAddr(), mRgba.getNativeObjAddr(), mComparisonMat.getNativeObjAddr());
        */

        Bitmap bmp = Bitmap.createBitmap(getFrameWidth(), getFrameHeight(), Bitmap.Config.ARGB_8888);

        if (android.MatToBitmap(mRgba, bmp))
            return bmp;

        bmp.recycle();
        return null;
    }

    /**
     * Initializes new and cleans old frames
     */
    @Override
    public void run() {
        super.run();

        synchronized (this) {
            // Explicitly deallocate Mats
            if (mYuv != null)
                mYuv.dispose();
            if (mRgba != null)
                mRgba.dispose();
            if (mGraySubmat != null)
                mGraySubmat.dispose();
            if (mIntermediateMat != null)
                mIntermediateMat.dispose();
            if (mComparisonMat != null)
                mComparisonMat.dispose();

            mYuv = null;
            mRgba = null;
            mGraySubmat = null;
            mIntermediateMat = null;
            mComparisonMat = null;
        }
    }

    /**
     * Compares the current frame (matAddrGr) against a given Mat (matComp) and draws the
     * comparison onto matAddrRgba
     * @param matAddrGr
     * @param matAddrRgba
     * @param matComp
     */
    public native void FindFeatures(long matAddrGr, long matAddrRgba, long matComp);

    /**
     * Loads native libraries
     */
    static {
        System.loadLibrary("feature_comparison");
    }
}
