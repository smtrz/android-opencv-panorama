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
import java.lang.InterruptedException;
import java.lang.Object;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.util.Log;

public class PanTiltCapture extends AsyncTask<Integer, Void, Integer>
     implements Camera.PictureCallback {

    private Object semaphore = new Object();

    protected Integer doInBackground(Integer... unused) {
        Camera c = Camera.open();
        c.setPreviewCallback(null);

        try {
            c.setPreviewDisplay(null);
        } catch (IOException e) {
        }


        for (int i=0; i<10; i++) {
            boolean success = false;
            while (!success) {
              try {
                Log.i("jpegCallback", "Start preview");
                c.startPreview();
                success = true;
              } catch (java.lang.RuntimeException e) {
              }
            }

            success = false;
            while (!success) {
              try {
                Log.i("jpegCallback", "Take picture");
                c.takePicture(null, null, this);
                success = true;
              } catch (java.lang.RuntimeException e) {
              }
            }

            try {
              synchronized(semaphore) {
                semaphore.wait();
              }
            } catch (InterruptedException e) {
            }
            c.stopPreview();
        }

        c.release();
        return 0;
    }

    public void onPictureTaken(byte[] data, Camera camera) {
        Log.i("jpegCallback", "Picture taken!");
        synchronized(semaphore) {
          semaphore.notify();
        }
    };
}
