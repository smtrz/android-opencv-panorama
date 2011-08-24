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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Gallery;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ViewSwitcher.ViewFactory;

public class PanoActivity extends Activity implements ViewFactory, OnClickListener {
    // Debugging
    private static final String TAG = "PanoActivity";
    private static final boolean D = true;

    public static final String SETTINGS                = "Pano_Settings";

    // persistent settings keys
    private final String SETTINGS_SAVE_PATH            = "path";
    private final String SETTINGS_IMAGE_PREFIX         = "image";
    private final String SETTINGS_OUTPUT_IMAGE         = "output";
    private final String SETTINGS_WARP_TYPE            = "warp";
    private final String SETTINGS_MATCH_CONF           = "match_conf";
    private final String SETTINGS_CONF_THRESH          = "conf_thresh";
    private final String SETTINGS_SHOW_TIP             = "show_tip";

    // default settings
    private String mDefaultPath;
    private String mDefaultImagePrefix;
    private String mDefaultOutputName                  = "result.jpg";
    private String mDefaultWarpType                    = "spherical";
    private String mDefaultMatchConf                   = "0.5";
    private String mDefaultConfThresh                  = "0.8";
    private boolean mDefaultShowTip                    = true;

    // possible dialogs to open
    public static final int DIALOG_RESULTS             = 0;
    public static final int DIALOG_STITCHING           = 1;
    public static final int DIALOG_FEATURE_COMPARISON  = 2;
    public static final int DIALOG_FEATURE_ALPHA       = 3;
    public static final int DIALOG_SUCCESS             = 4;
    public static final int DIALOG_ERROR               = 5;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 301;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;

    // runtime setting
    private SharedPreferences mSettings;
    private String mDirPath;
    private String mImagePrefix;
    private String mOutputImage;
    private String mWarpType;
    private String mMatchConf;
    private String mConfThresh;
    private String mSubDir = null;
    private boolean mShowTip;

    private String mType                               = ".jpg";
    public static final String MIME_TYPE               = "image/jpg";
    private int mCurrentImage                          = 1;
    private int mGalleryImage                          = 0;
    private ImageSwitcher mImageSwitcher;
    private PanoAdapter mPanoAdapter;
    private List<File> mDirectories;

    private Button mShareButton;
    private Button mUploadButton;
    private Button mRestitchButton;

    /**
     * Called when activity is first created.
     * Initializes the default storage locations
     * Initializes the ImageSwitcher and Button visibility
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Initialize Dynamic Defaults
        mDefaultPath = Environment.getExternalStorageDirectory().toString()
                + "/" + getResources().getString(R.string.default_folder) + "/";
        mDefaultImagePrefix = getResources().getString(R.string.default_prefix);

        // Initialize Our Settings
        loadSettings(SETTINGS);

        setContentView(R.layout.main);

        // Initialize the gallery view
        mPanoAdapter = new PanoAdapter(this);
        mImageSwitcher = (ImageSwitcher) findViewById(R.id.main_switcher);
        mImageSwitcher.setFactory(this);

        Gallery gallery = (Gallery) findViewById(R.id.main_gallery);
        gallery.setAdapter(mPanoAdapter);
        gallery.setOnItemClickListener(mPanoAdapter);

        mShareButton = (Button) findViewById(R.id.main_button_share);
        mShareButton.setVisibility(View.INVISIBLE);
        mShareButton.setOnClickListener(this);

        mUploadButton = (Button) findViewById(R.id.main_button_upload);
        mUploadButton.setVisibility(View.INVISIBLE);
        mUploadButton.setOnClickListener(this);

        mRestitchButton = (Button) findViewById(R.id.main_button_restitch);
        mRestitchButton.setVisibility(View.INVISIBLE);
        mRestitchButton.setOnClickListener(this);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void onStart() {
        super.onStart();

        // If Bluetooth is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        refreshView();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it.
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mBluetoothChatHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    break;
                }
                break;
            case MESSAGE_WRITE:
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    private void setupChat() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mBluetoothChatHandler);

        // Initialize the buffer for outgoing messages
        //mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Initializes all the settings
     */
    private boolean loadSettings(String arg) {
        mSettings = getSharedPreferences(arg, Context.MODE_PRIVATE);
        mDirPath = mSettings.getString(SETTINGS_SAVE_PATH, mDefaultPath);
        mImagePrefix = mSettings.getString(SETTINGS_IMAGE_PREFIX, mDefaultImagePrefix);
        mOutputImage = mSettings.getString(SETTINGS_OUTPUT_IMAGE, mDefaultOutputName);
        mWarpType = mSettings.getString(SETTINGS_WARP_TYPE, mDefaultWarpType);
        mMatchConf = mSettings.getString(SETTINGS_MATCH_CONF, mDefaultMatchConf);
        mConfThresh = mSettings.getString(SETTINGS_CONF_THRESH, mDefaultConfThresh);
        mShowTip = mSettings.getBoolean(SETTINGS_SHOW_TIP, mDefaultShowTip);
        return true;
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     * TODO: This probably isn't the best class to hold this method, since it's
     * only used by PanTiltCapture.
     */
    public void SendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
        }
    }


    /**
     * Generates a new folder name, creates the folder, and returns the name
     */
    private void createNewPano() {
        createNewPano(true);
    }

    /**
     * Generates a new folder name, creates the folder, and returns the name
     */
    private void createNewPano(boolean capture) {
        // generate the default folder name
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        StringBuilder b = new StringBuilder(df.format(new Date()));

        mSubDir = b.toString()+ "/";
        mCurrentImage = 1;

        if (capture) {
            if (mShowTip) showDialog(DIALOG_FEATURE_COMPARISON);
            else capturePhoto();
        }
    }

    /**
     * Builds a new Intent to be passed to Camera Activity for taking a picture
     * @return Intent
     */
    private Intent createCaptureIntent() {
        if (mSubDir == null) createNewPano(false);
        Intent intent = new Intent(
                MediaStore.ACTION_IMAGE_CAPTURE,
                Uri.fromFile(new File(mDirPath + mSubDir + mImagePrefix + mCurrentImage + mType)),
                getApplicationContext(), PanoCamera.class);
        intent.putExtra(PanoCamera.EXTRA_DIR_PATH, mDirPath + mSubDir);
        intent.putExtra(PanoCamera.EXTRA_FILE_NAME,
                mImagePrefix + mCurrentImage + mType);
        return intent;
    }

    /**
     * Starts a new activity with generated Intent
     * Attempts to close the FEATURE_COMPARISON Tip Dialog.
     * Continue if it hasn't been shown since we only want to make sure it isn't open
     */
    private void capturePhoto() {
        try {
            dismissDialog(DIALOG_FEATURE_COMPARISON);
        } catch (IllegalArgumentException e) {
            // Catch and continue, make sure dialog is gone (see method doc)
        }
        startActivityForResult(createCaptureIntent(), PanoCamera.INTENT_TAKE_PICTURE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_advanced:
            // Show advanced editor
            return true;
        case R.id.menu_pantilt_capture:
            PanTiltCapture pan_tilt_capture = new PanTiltCapture(mDefaultPath,
                                                                 mDefaultImagePrefix,
                                                                 mType);
            pan_tilt_capture.SetCaller(this);
            pan_tilt_capture.execute();
            return true;
        case R.id.menu_connect:
            // Connect to pan/tilt head
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, DeviceListActivity.INTENT_CONNECT_TO_PANTILT);
            return true;
        }
      return true;
    }

    /**
     * Processes data from start activity for results
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PanoCamera.INTENT_TAKE_PICTURE) {
            if (resultCode == RESULT_OK) {
                showDialog(DIALOG_RESULTS);
            }
        } else if (requestCode == PicasaUploadActivity.INTENT_UPLOAD_IMAGE) {
            if (resultCode == RESULT_OK) {
                // Success
                Toast.makeText(this, R.string.success_uploaded, Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                // File Not Found
                Toast.makeText(this, R.string.file_404, Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_FIRST_USER) {
                // Network Error
                Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.protocol_error, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == DeviceListActivity.INTENT_CONNECT_TO_PANTILT) {
            Log.i(TAG, "Connecting to pantilt");
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
        } else if (requestCode == REQUEST_ENABLE_BT) {
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "Bluetooth now enabled.");
                // Bluetooth is now enabled
            } else {
                // User did not enable Bluetooth or an error occured
            }
        }
    }

    /**
     * Creates dialog static data
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder;
        switch(id) {
        case DIALOG_FEATURE_COMPARISON:
            Dialog tip = new Dialog(this);
            tip.setContentView(R.layout.tip);
            tip.setTitle(R.string.dialog_tip);
            tip.setCancelable(true);

            dialog = tip;
            break;
        case DIALOG_RESULTS:
            Dialog progress = new Dialog(this);
            progress.setContentView(R.layout.result);
            progress.setTitle(R.string.dialog_results_title);
            progress.setCancelable(false);

            dialog = progress;
            break;
        case DIALOG_STITCHING:
            ProgressDialog stitching = ProgressDialog.show(PanoActivity.this, "",
                    getResources().getString(R.string.dialog_stitching), true);
            stitching.setCancelable(false);
            dialog = stitching;
            break;
        case DIALOG_ERROR:
            builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialog_error)
                   .setCancelable(false)
                   .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        PanoActivity.this.finish();
                    }
                });
            dialog = builder.create();
            break;
        case DIALOG_SUCCESS:
            Dialog success = new Dialog(this);
            success.setContentView(R.layout.success);
            success.setTitle(R.string.success);
            success.setCancelable(false);

            dialog = success;
            break;
        default:
            dialog = null;
            break;
        }
        return dialog;
    }

    /**
     * Prepares dialogs dynamic content
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
        case DIALOG_RESULTS:
            refreshView();

            Mat mIntermediate = new Mat();
            Mat mYuv = new Mat();

            mIntermediate = Highgui.imread(mDirPath +mSubDir+
                    mImagePrefix + mCurrentImage + mType);

            Core.transpose(mIntermediate, mYuv);
            Core.flip(mYuv, mIntermediate, 1);

            Imgproc.resize(mIntermediate, mYuv, new Size(), 0.25, 0.25, Imgproc.CV_INTER_AREA);

            /** Currently Not Working in OpenCV **/
            /*
            Bitmap jpg = Bitmap.createBitmap(mIntermediate.cols(), mIntermediate.rows(),
                    Bitmap.Config.ARGB_8888);
            android.MatToBitmap(mIntermediate, jpg);
            */
            /** So we resort to this method **/
            Highgui.imwrite(mDirPath +mSubDir+ mImagePrefix +
                    mCurrentImage + ".png", mYuv);
            Bitmap jpg = BitmapFactory.decodeFile(mDirPath +mSubDir+
                    mImagePrefix + mCurrentImage + ".png");

            // cleanup
            mIntermediate.dispose();
            mYuv.dispose();

            ImageView image = (ImageView) dialog.findViewById(R.id.image);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setAdjustViewBounds(true);
            image.setPadding(2, 2, 2, 2);
            image.setImageBitmap(jpg);

            Button capture = (Button) dialog.findViewById(R.id.capture);
            capture.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    mCurrentImage++;
                    capturePhoto();
                }
            });
            Button retake = (Button) dialog.findViewById(R.id.retake);
            retake.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    capturePhoto();
                }
            });
            Button stitch = (Button) dialog.findViewById(R.id.stitch);
            stitch.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    new StitchPhotoTask().execute();
                }
            });

            break;
        case DIALOG_SUCCESS:
            final File img = new File(mDirPath + mSubDir + mOutputImage);
            Bitmap result = BitmapFactory.decodeFile(img.getAbsolutePath());

            ImageView png = (ImageView) dialog.findViewById(R.id.image);
            png.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            png.setAdjustViewBounds(true);
            png.setPadding(3, 3, 3, 3);
            png.setImageBitmap(result);

            refreshView();

            Button share = (Button) dialog.findViewById(R.id.share);
            share.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    shareImage(img);
                    dismissDialog(DIALOG_SUCCESS);
                }
            });

            Button upload = (Button) dialog.findViewById(R.id.upload);
            upload.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    uploadImage(img);
                    dismissDialog(DIALOG_SUCCESS);
                }
            });

            Button exit = (Button) dialog.findViewById(R.id.close);
            exit.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    dismissDialog(DIALOG_SUCCESS);
                }
            });
            break;
        case DIALOG_FEATURE_COMPARISON:
            CheckBox box = (CheckBox) dialog.findViewById(R.id.show_tip);
            box.setChecked(mShowTip);
            box.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    mShowTip = ((CheckBox)v).isChecked();
                    SharedPreferences settings = getSharedPreferences(SETTINGS, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean(SETTINGS_SHOW_TIP, mShowTip);
                    editor.commit();
                }
            });

            Button cont = (Button) dialog.findViewById(R.id.tip_continue);
            cont.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    capturePhoto();
                }
            });
            break;
        }
    }

    /**
     * Stitches together the set of images and presents the result to the user via a Dialog
     */
    class StitchPhotoTask extends AsyncTask<Void, Void, Integer> {

        /**
         * Shows a Progress Dialog to the user
         * Attempts to close the RESULTS dialog. We just catch the execption if it isn't open
         * and continue, since we really only care about ensuring that the dialog is closed.
         */
        @Override
        protected void onPreExecute() {
            try {
                dismissDialog(DIALOG_RESULTS);
            } catch (IllegalArgumentException e) {
                // catch and continue, we just want to make sure the dialog is gone
            }
            // Try to free up some memory before stitching
            System.gc();
            showDialog(DIALOG_STITCHING);
        }

        /**
         * Stitches the images
         * Passes data to native code via string array
         */
        @Override
        protected Integer doInBackground(Void... v) {
            List<String> s = new ArrayList<String>();
            s.add("Stitch");
            for (int i = 0; i < mCurrentImage; i++) {
                s.add(mDirPath + mSubDir + mImagePrefix + (i+1) + ".png");
            }
            s.add("--warp");
            s.add(mWarpType);
            s.add("--conf_thresh");
            s.add(mConfThresh);
            s.add("--match_conf");
            s.add(mMatchConf);
            s.add("--work_megapix");
            s.add("0.2");
            s.add("--seam_megapix");
            s.add("0.2");
            s.add("--expos_comp");
            s.add("gain");
            s.add("--output");
            s.add(mDirPath + mSubDir + mOutputImage);
            return Stitch(s.toArray());
        }

        /**
         * Builds response for user based on stitch status
         */
        @Override
        protected void onPostExecute(Integer ret) {
            dismissDialog(DIALOG_STITCHING);
            if (ret == 0) showDialog(DIALOG_SUCCESS);
            else showDialog(DIALOG_ERROR);
        }
    }

    /**
     * Natively stitches images together. Takes a string array of equivalent command line arguments
     * @param args
     * @return
     */
    public native int Stitch(Object[] args);

    /**
     * Loads Native Libraries
     */
    static {
        System.loadLibrary("precomp");
        System.loadLibrary("util");
        System.loadLibrary("matchers");
        System.loadLibrary("autocalib");
        System.loadLibrary("blenders");
        System.loadLibrary("exposure_compensate");
        System.loadLibrary("motion_estimators");
        System.loadLibrary("seam_finders");
        System.loadLibrary("warpers");
        System.loadLibrary("opencv_stitcher");
    }

    /**
     * Displays Gallery View to User.
     * Updates ImageView on Click.
     */
    public class PanoAdapter extends BaseAdapter implements OnItemClickListener {
        private Context mContext;
        private int itemBackground;

        PanoAdapter(Context c) {
            mContext = c;

            TypedArray typeArray = obtainStyledAttributes(R.styleable.main_gallery);
            itemBackground = typeArray.getResourceId(
                R.styleable.main_gallery_android_galleryItemBackground, 0);
            typeArray.recycle();
            updateFolders();
        }

        @Override
        public int getCount() {
            return mDirectories.size() + 1;
        }

        @Override
        public Object getItem(int arg0) {
            return arg0;
            }

        @Override
        public long getItemId(int arg0) {
            return arg0;
        }

        /**
         * Generates tumbnails for past panoramas
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView panos = new ImageView (mContext);
            Bitmap result = null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            // initialize our new pano image view
            if (position == 0) {
                result = BitmapFactory.decodeResource(getResources(), R.drawable.ic_add, options);
            }
            // otherwise initialize the sorted panorama's
            else {
                File f = getDirImage(position-1);
                if (f != null) {
                    result = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
                }
            }
            panos.setImageBitmap(result);
            panos.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            panos.setLayoutParams(new Gallery.LayoutParams(150, 150));
            panos.setBackgroundResource(itemBackground);
            return panos;
        }

        /**
         * Updates ImageView or starts a new pano
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            mGalleryImage = position;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            if (position == 0) {
                mShareButton.setVisibility(View.INVISIBLE);
                mUploadButton.setVisibility(View.INVISIBLE);
                mRestitchButton.setVisibility(View.INVISIBLE);
                createNewPano();
            }
            else {
                mShareButton.setVisibility(View.VISIBLE);
                mUploadButton.setVisibility(View.VISIBLE);
                mRestitchButton.setVisibility(View.VISIBLE);
                mCurrentImage = getDirCount(position-1);
                File f = getDirImage(position-1);
                if (f != null) {
                    mSubDir = f.getParentFile().getName() + "/";
                    mImageSwitcher.setImageDrawable(new BitmapDrawable(
                            BitmapFactory.decodeFile(getDirImage(position-1).getAbsolutePath(),
                                                     options)));
                }
            }
        }
    }

    /**
     * Refreshes the Gallery View
     */
    private void refreshView() {
        updateFolders();
        mPanoAdapter.notifyDataSetChanged();
    }

    /**
     * Refreshes the list of current folders used to build the gallery view
     */
    private void updateFolders() {
        File storage = new File(mDirPath);
        if (!storage.exists()) storage.mkdirs();
        File[] contents = storage.listFiles();
        mDirectories = new ArrayList<File>();

        for (int i = 0; i < contents.length; i++) {
            if (contents[i].isDirectory()) {
                mDirectories.add(contents[i]);
            }
        }
    }

    /**
     * Determines the number of pictures taken for the folder at index
     * @param index
     * @return The number of images.
     */
    private int getDirCount(int index) {
        File[] contents = mDirectories.get(index).listFiles();
        int hasResult = -1;
        int hasFirst = -1;
        int size = 0;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i].isFile()) {
                if (contents[i].getName().contains("png")) size++;
                if (contents[i].getName().equals(mOutputImage)) hasResult = i;
                else if (contents[i].getName().equals(mImagePrefix + 1 + ".png")) hasFirst = i;
            }
        }
        if ((hasResult < 0) && (hasFirst < 0)) {
            return 0;
        }

        if (hasResult >= 0) return (size-1);
        else return size;
    }

    /**
     * Returns the file do display in the Gallery view or Image View to user for the given index
     * @param index
     * @return the File
     */
    private File getDirImage(int index) {
        File[] contents = mDirectories.get(index).listFiles();
        int hasResult = -1;
        int hasFirst = -1;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i].isFile()) {
                if (contents[i].getName().equals(mOutputImage)) hasResult = i;
                else if (contents[i].getName().equals(mImagePrefix + 1 + ".png")) hasFirst = i;
            }
        }
        if ((hasResult < 0) && (hasFirst < 0)) {
            return null;
        }

        if (hasResult >= 0) return contents[hasResult];
        else return contents[hasFirst];
    }

    /**
     * Creates the Image View that displays the panorama
     */
    @Override
    public View makeView() {
        ImageView imageView = new ImageView(this);
        imageView.setBackgroundColor(0xFF000000);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setLayoutParams(new ImageSwitcher.LayoutParams(
                LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
        return imageView;
    }

    /**
     * Handles the different tasks for the different button clicks on the main screen
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.main_button_upload:
            uploadImage(getDirImage(mGalleryImage-1));
            break;
        case R.id.main_button_share:
            shareImage(getDirImage(mGalleryImage-1));
            break;
        case R.id.main_button_restitch:
            new StitchPhotoTask().execute();
            break;
        }
    }

    /**
     * Uploads the file f to Picasa
     * @param f
     */
    private void uploadImage(File f) {
        Intent upload = new Intent(this, PicasaUploadActivity.class);
        upload.putExtra(PicasaUploadActivity.EXTRA_FILE, f.getAbsolutePath());
        startActivityForResult(upload, PicasaUploadActivity.INTENT_UPLOAD_IMAGE);
    }

    /**
     * Shares the file f through a generic intent
     * @param f
     */
    private void shareImage(File f) {
        Intent shareImage = new Intent(android.content.Intent.ACTION_SEND);
        shareImage.setType(MIME_TYPE);
        shareImage.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
        startActivity(Intent.createChooser(shareImage,
                getResources().getString(R.string.intent_share_using)));
    }
}
