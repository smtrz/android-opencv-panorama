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

import android.content.Context;
import android.graphics.Bitmap;
import android.view.SurfaceHolder;

/**
 * Does frame by frame computations. Currently only converts from YUV to RGB
 */
class PanoSurfaceView extends PanoSurfaceBase {
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
        }
    }

    /**
     * Processes each preview frame; currently only converts it to RGB
     */
    @Override
    protected Bitmap processFrame(byte[] data) {
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
        }
    }
}
