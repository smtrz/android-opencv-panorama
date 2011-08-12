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

package net.pandorica;

import java.io.File;
import java.io.FileOutputStream;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * Instantiates the camera previewing via custom view
 * Saves photo jpeg callback data to sdcard
 */
public class PanoCamera extends Activity {
    public static final String  EXTRA_DIR_PATH        = "dirPath";
    public static final String  EXTRA_FILE_NAME       = "name";

    public static final int     INTENT_TAKE_PICTURE   = 0;

    private String              mDirPath              = null;
    private String              mFileName             = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(new PanoSurfaceView(this, this));

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mDirPath = extras.getString(PanoCamera.EXTRA_DIR_PATH);
            mFileName = extras.getString(PanoCamera.EXTRA_FILE_NAME);
        }
    }

    /**
     * Writes photo jpeg data to sdcard
     */
    class SavePhotoTask extends AsyncTask<byte[], Void, Integer> {
        @Override
        protected Integer doInBackground(byte[]... data) {
            /* file save format currently looks like photo-20110602133259329.jpg
             */
            if (mDirPath == null || mFileName == null) return Activity.RESULT_CANCELED;
            for (int i = 0; i < data.length; i++) {
                File dir = new File(mDirPath);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File photo = new File(mDirPath, mFileName);

                if (photo.exists()) {
                    photo.delete();
                }

                try {
                    FileOutputStream fos = new FileOutputStream(photo.getAbsolutePath());
                    fos.write(data[i]);
                    fos.close();

                } catch (java.io.IOException e) {
                    return Activity.RESULT_FIRST_USER;
                }
            }

            return Activity.RESULT_OK;
        }

        /**
         * Finalizes return data and returns to caller.
         */
        @Override
        protected void onPostExecute(Integer result) {
            setResult(result, new Intent());
            finish();
        }
    }
}
