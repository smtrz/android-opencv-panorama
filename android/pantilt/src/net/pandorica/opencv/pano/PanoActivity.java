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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
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
    private final String SETTINGS_IMAGE_PREFIX         = "pano";
    private final String SETTINGS_OUTPUT_IMAGE         = "output";

    // default settings
    private String mDefaultPath;
    private String mDefaultImagePrefix;
    private String mDefaultOutputName                  = "result.jpg";

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
    private String mSubDir = null;

    private String mType                               = ".jpg";
    public static final String MIME_TYPE               = "image/jpg";
    private int mCurrentImage                          = 1;
    private int mGalleryImage                          = 0;
    private ImageSwitcher mImageSwitcher;
    private PanoAdapter mPanoAdapter;
    private List<File> mDirectories;

    private Button mShareButton;

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
    }

    /**
     * Initializes all the settings
     */
    private boolean loadSettings(String arg) {
        mSettings = getSharedPreferences(arg, Context.MODE_PRIVATE);
        mDirPath = mSettings.getString(SETTINGS_SAVE_PATH, mDefaultPath);
        mImagePrefix = mSettings.getString(SETTINGS_IMAGE_PREFIX, mDefaultImagePrefix);
        mOutputImage = mSettings.getString(SETTINGS_OUTPUT_IMAGE, mDefaultOutputName);
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
            // TODO: better error handling here
            //Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
        }
    }


    private void createNewPano() {
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            // Connect to pan/tilt head
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, DeviceListActivity.INTENT_CONNECT_TO_PANTILT);
            return;
        }

        PanTiltCapture pan_tilt_capture = new PanTiltCapture(mDefaultPath,
                                                             mDefaultImagePrefix,
                                                             mType);
        pan_tilt_capture.SetCaller(this);
        pan_tilt_capture.execute();
    }

    /**
     * Processes data from start activity for results
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DeviceListActivity.INTENT_CONNECT_TO_PANTILT) {
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
         * Generates thumbnails for past panoramas
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView panos = new ImageView (mContext);
            Bitmap result = null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            // initialize the new pano icon
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
                createNewPano();
            }
            else {
                mShareButton.setVisibility(View.VISIBLE);
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

    private File getDir(int index) {
        File directory = mDirectories.get(index);
        return directory;
    }

    /**
     * Returns the file to display in the Gallery view or Image View to user for the given index
     * @param index
     * @return the File
     */
    private File getDirImage(int index) {
        File[] files = mDirectories.get(index).listFiles();
        for (File f: files) {
            String filename = f.getName();
            if (filename.endsWith(mOutputImage)) {
                  return f;
            }
        }
        return null;
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
        case R.id.main_button_share:
            sharePano(getDir(mGalleryImage-1));
            break;
        }
    }

    /**
     * Shares the file f through a generic intent
     * @param f
     */
    private void sharePano(File directory) {
        ArrayList<Uri> uris = new ArrayList<Uri>();
        File[] files = directory.listFiles();
        for (File file: files) {
            Log.i(TAG, "Sharing: " + file);
            Uri uri = Uri.fromFile(file);
            uris.add(uri);
        }

        Intent share = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
        share.setType(MIME_TYPE);
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivity(Intent.createChooser(share,
                getResources().getString(R.string.intent_share_using)));
    }
}
