package net.pandorica.opencv.pano;

import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class AdvancedMenuActivity extends ListActivity implements OnItemClickListener {
    private final static int DIALOG_CAMERA_RESOLUTION = 0;
    private SharedPreferences mSettings;

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettings = getSharedPreferences(PanoActivity.SETTINGS, Context.MODE_PRIVATE);


        String[] menuItems = getResources().getStringArray(R.array.settings_menu_items);
        setListAdapter(new ArrayAdapter<String>(this, R.layout.menu_list_item, menuItems));

        ListView listView = getListView();
        listView.setTextFilterEnabled(true);

        listView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // User selected the Camera Resolution menu option
        if (((TextView)view).getText().equals(
                getResources().getString(R.string.camera_resolution))) {
            showDialog(DIALOG_CAMERA_RESOLUTION);
        }
    }
    
    private static List<Camera.Size> getSupportedSizes() {
        Camera camera = Camera.open();
        Parameters params = camera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
        camera.release();
        return sizes;
    }

    private int getCameraPosition() {
        List<Camera.Size> supported = getSupportedSizes();
        int[] size = PanoCamera.getCameraSize(getApplicationContext(),
                supported.get(0).width, supported.get(0).height);
        
        for (int i = 0; i < supported.size(); i++) {
            if ((supported.get(i).width == size[0]) && (supported.get(i).height == size[1])) {
                return i;
            }
        }
        return 0;
    }

    private void setCameraSize(Camera.Size size) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(PanoCamera.CAMERA_WIDTH, size.width);
        editor.putInt(PanoCamera.CAMERA_HEIGHT, size.height);
        editor.commit();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder;
        switch(id) {
        case DIALOG_CAMERA_RESOLUTION:
            final List<Camera.Size> supportedSizesList = getSupportedSizes();
            
            // convert supported sizes into a width x height string array
            CharSequence[] supportedSizes = new String[supportedSizesList.size()];
            for (int i = 0; i < supportedSizesList.size(); i++) {
                supportedSizes[i] = String.format("%dx%d", 
                        supportedSizesList.get(i).width,
                        supportedSizesList.get(i).height);
            }
            builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.camera_resolution_dialog));
            builder.setSingleChoiceItems(supportedSizes, 0 , new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface arg0, int position) {
                    setCameraSize(supportedSizesList.get(position));
                    dismissDialog(DIALOG_CAMERA_RESOLUTION);
                }
            });
            dialog = builder.create();
            break;
        default:
            dialog = null;
            break;
        }
        return dialog;
    }
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch(id) {
        case DIALOG_CAMERA_RESOLUTION:
            ((AlertDialog)dialog).getListView().setItemChecked(getCameraPosition(), true);
            break;
        default:
            super.onPrepareDialog(id, dialog);
            break;
        }
    }
}