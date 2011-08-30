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

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.Math;
import java.lang.Object;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class PanTiltCapture extends AsyncTask<Integer, String, Integer>
     implements Camera.PictureCallback {

    private Object semaphore = new Object();
    private PanoActivity mCaller;

    private String mPanoSubdirectory;  // The directory name where we put the images
    private String mBasePath;       // The directory holding all the pano subdirs
    private int mCurrentImage;
    private int mThumbnailX;
    private int mThumbnailY;
    private String mImagePrefix;
    private String mType;
    private Bitmap mThumbnailPano;
    private float mThumbnailScale = 0.1f;

    public PanTiltCapture(String base_path, String image_prefix, String type) {
      mBasePath = base_path;
      mImagePrefix = image_prefix;
      mType = type;
    }

    public void onPreExecute() {
        Log.i("jpegCallback", "In PreExecute");
    }

    protected Integer doInBackground(Integer... unused) {
        // generate the default folder name
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        StringBuilder b = new StringBuilder(df.format(new Date()));

        mPanoSubdirectory = b.toString();
        File dir = new File(mBasePath + mPanoSubdirectory + "/");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        mCurrentImage = 1;

        Camera c = Camera.open();
        c.setPreviewCallback(null);

        try {
            c.setPreviewDisplay(null);
        } catch (IOException e) {
        }

        // TODO: un-hard-code everything
        int image_width = 1920; // TODO: figure out what it actually is on this phone
        float hfov = 42; // Horizontal field of view
        int pano_thumbnail_width = Math.round(360 / hfov * image_width * mThumbnailScale);
        int pano_thumbnail_height = Math.round(2560 * mThumbnailScale);

        mThumbnailPano = Bitmap.createBitmap(pano_thumbnail_width, pano_thumbnail_height,
                                             Bitmap.Config.ARGB_8888);
        SetAlpha(mThumbnailPano, 0);

        mThumbnailY = 0;
        mThumbnailX = 0;

        int max_pan = 360;
        int pan_increment = 30;

        // TODO: The manual stitching code is pretty much hard coded for a
        // single pass horizontally.  Update it to support tilt.
        int max_tilt = 1; // Only do a cylindrical pano for now
        int tilt_increment = 20;
        for (int tilt = 0; tilt < max_tilt; tilt += tilt_increment) {
            publishProgress("Moving pan/tilt head");
            mCaller.SendMessage("p0\r");
            try { Thread.sleep(2000); } catch (InterruptedException e) { }
            mCaller.SendMessage("t" + tilt + "\r");
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            mCaller.SendMessage("o\r");

            for (int pan = 0; pan < max_pan; pan += pan_increment) {
                publishProgress("Moving to " + pan + "/" + tilt + " degrees");

                // Start the preview before we move the head so it has a chance
                // to do AWB and whatnot.
                boolean success = false;
                while (!success) {
                  try {
                    Log.i("jpegCallback", "Start preview");
                    c.startPreview();
                    success = true;
                  } catch (java.lang.RuntimeException e) {
                  }
                }

                mCaller.SendMessage("p" + pan + "\r");
                try { Thread.sleep(1000); } catch (InterruptedException e) { }
                mCaller.SendMessage("o" + pan + "\r");

                mThumbnailX = (int) Math.round(pano_thumbnail_width * (360 - pan) / 360.0 -
                                               (image_width * mThumbnailScale))  ;

                Log.i("jpegCallback", "mThumbnailX is " + mThumbnailX);
                if (mThumbnailX < 0) mThumbnailX = 0;

                // TODO: Get back a message from pantilt when it's finished
                // moving

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
                mCurrentImage++;
            }
        }

        publishProgress("Resetting to initial position");
        mCaller.SendMessage("p0\r");
        try { Thread.sleep(2000); } catch (InterruptedException e) { }
        mCaller.SendMessage("t0\r");
        try { Thread.sleep(2000); } catch (InterruptedException e) { }
        mCaller.SendMessage("o\r");

        try {
            FileOutputStream out = new FileOutputStream(mBasePath + mPanoSubdirectory +
                                                        "/pano_thumbnail_" + mPanoSubdirectory +
                                                        ".jpg");
            mThumbnailPano.compress(Bitmap.CompressFormat.JPEG, 60, out);
        } catch (Exception e) {
            // TODO: Do something useful here
        }
        c.release();
        publishProgress("Panorama successfully completed");
        return 0;
    }

    protected final void onProgressUpdate(String... message) {
        Toast.makeText(mCaller.getApplicationContext(),
                       message[0], Toast.LENGTH_SHORT).show();
    }

    private void SetAlpha(Bitmap b, int alpha) {
      int height = b.getHeight();
      int width = b.getWidth();
      for (int y=0; y < height; y++) {
        for (int x=0; x < width; x++) {
          int c = b.getPixel(x,y);
          int argb = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c));
          b.setPixel(x, y, argb);
        }
      }
    }

    public void onPictureTaken(byte[] data, Camera camera) {
        Log.i("jpegCallback", "Picture taken!");

        String filename = mImagePrefix + "_" + mPanoSubdirectory + "_" + mCurrentImage + mType;
        String full_filename = mBasePath + mPanoSubdirectory + "/" + filename;
        Log.i("jpegCallback", "Writing " + full_filename);

        File photo = new File(mBasePath + mPanoSubdirectory, filename);
        try {
            FileOutputStream fos = new FileOutputStream(photo.getAbsolutePath());
            fos.write(data);
            fos.close();
        } catch (java.io.IOException e) {
            // TODO: something useful here
        }

        // Slap the image into the pano thumbnail
        BitmapFactory.Options scale_factor = new BitmapFactory.Options();
        // TODO: resolve this with mThumbnailScale
        scale_factor.inSampleSize = 10;
        // Um, yeah.  This just scales the 5MP image down to 320x240
        Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, scale_factor);
        // TODO: get rid of 256, 192
        Bitmap actually_scaled = Bitmap.createScaledBitmap(image, 256, 192, true);
        Matrix rotate = new Matrix();
        rotate.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(actually_scaled,
            0, 0,
            Math.round(actually_scaled.getWidth()),
            Math.round(actually_scaled.getHeight()),
            rotate, true);
        Log.i("jpegCallback", "rotated is " + rotated.getWidth() + " by " + rotated.getHeight());

        int region_width = rotated.getWidth();
        if (mThumbnailX + region_width > mThumbnailPano.getWidth()) {
            region_width = mThumbnailPano.getWidth() - mThumbnailX;
        }
        int region_height = rotated.getHeight();
        if (mThumbnailY + region_height > mThumbnailPano.getHeight()) {
            region_height = mThumbnailPano.getHeight() - mThumbnailY;
        }

        int buffer_size = region_width * region_height * 4;
        int[] new_image = new int[buffer_size];
        int[] existing_image = new int[buffer_size];

        rotated.getPixels(new_image, 0, rotated.getWidth(),
                         0, 0, rotated.getWidth(), rotated.getHeight());

        mThumbnailPano.getPixels(existing_image, 0, rotated.getWidth(),
                         mThumbnailX, mThumbnailY, rotated.getWidth(), rotated.getHeight());

        // Manually average pixels.  TODO: I'm sure there's a better way to do
        // this.
        for (int i=0; i < buffer_size; i++) {
            if (Color.alpha(existing_image[i]) == 0) {
                existing_image[i] = new_image[i];
            } else {
                int new_color = Color.argb(255,
                    (Color.red(existing_image[i]) + Color.red(new_image[i])) / 2,
                    (Color.green(existing_image[i]) + Color.green(new_image[i])) / 2,
                    (Color.blue(existing_image[i]) + Color.blue(new_image[i])) / 2
                    );
                existing_image[i] = new_color;
            }
        }

        Log.i("jpegCallback", "Pasting thumbnail at " + mThumbnailX);

        mThumbnailPano.setPixels(existing_image, 0, rotated.getWidth(), mThumbnailX,
                                 mThumbnailY, region_width, region_height);

        synchronized(semaphore) {
          semaphore.notify();
        }
    }

    public void SetCaller(PanoActivity caller) {
        mCaller = caller;
    }

    protected void onPostExecute(Integer unused) {
        mCaller.refreshView();
    }
}
