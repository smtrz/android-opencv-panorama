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

package com.google.code.panoforandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleUrl;
import com.google.api.client.googleapis.extensions.android2.auth.GoogleAccountManager;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.xml.atom.AtomParser;
import com.google.api.client.xml.XmlNamespaceDictionary;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

/**
 * Manages google.com authToken and uploads images to their Picasa Drop Box
 */
public class PicasaUploadActivity extends Activity {
    public static final int     INTENT_UPLOAD_IMAGE   = 1;

    private static final String TAG                   = "GoogleAuthenticator";
    // http://code.google.com/apis/gdata/faq.html#clientlogin
    private static final String AUTH_TOKEN_TYPE       = "lh2";
    private static final int    REQUEST_AUTHENTICATE  = 0;
    static final String         EXTRA_FILE            = "file";
    private static final int    DIALOG_UPLOADING      = 0;

    static final String         PREF                  = TAG;
    private static final String PREF_ACCOUNT_NAME     = "accountName";
    private static final String PREF_AUTH_TOKEN       = "authToken";
    private static final String PREF_GSESSIONID       = "gsessionid";
    private static final String AUTH_ACCOUNT          = "com.google";

    static final XmlNamespaceDictionary DICTIONARY =
        new XmlNamespaceDictionary().set(
                        "", "http://www.w3.org/2005/Atom").set(
                        "batch", "http://schemas.google.com/gdata/batch").set(
                        "gd", "http://schemas.google.com/g/2005");

    private HttpRequestFactory  mRequestFactory;
    private GoogleAccountManager mAccountManager;
    private SharedPreferences   mSettings;

    private String              mAccountName;
    private String              mAuthToken;
    private String              mGSessionId;

    /**
     * Creates the Activity
     * Creates a Transport that the uploader can use (adds authToken to HTTP Headers)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mAccountManager = new GoogleAccountManager(this);
        mSettings = this.getSharedPreferences("prefs", 0);

        mAuthToken = mSettings.getString(PREF_AUTH_TOKEN, null);
        mAccountName = mSettings.getString(PREF_ACCOUNT_NAME, null);
        mGSessionId = mSettings.getString(PREF_GSESSIONID, null);

        NetHttpTransport transport = new NetHttpTransport();
        mRequestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) {
                    GoogleHeaders headers = new GoogleHeaders();
                    headers.setApplicationName("PanoForAndroid");
                    headers.gdataVersion = "2";
                    headers.setGoogleLogin(mAuthToken);
                    request.headers = headers;
                    AtomParser parser = new AtomParser();
                    parser.namespaceDictionary = DICTIONARY;
                    request.addParser(parser);

                    request.interceptor = new HttpExecuteInterceptor() {
                            @Override
                            public void intercept(HttpRequest request) throws IOException {
                                GoogleHeaders headers = (GoogleHeaders) request.headers;
                                headers.setGoogleLogin(mAuthToken);
                                request.url.set("gsessionid", mGSessionId);
                            }
                    };
                    request.unsuccessfulResponseHandler = new HttpUnsuccessfulResponseHandler() {
                            @Override
                            public boolean handleResponse(HttpRequest request,
                                    HttpResponse response, boolean retrySupported)
                                            throws IOException {
                                if (response.statusCode == 302) {
                                    GoogleUrl url = new GoogleUrl(response.headers.location);
                                    mGSessionId = (String) url.getFirst("gsessionid");
                                    SharedPreferences.Editor editor = mSettings.edit();
                                    editor.putString(PREF_GSESSIONID, mGSessionId);
                                    editor.commit();
                                    return true;
                                } else if (response.statusCode == 401 || response.statusCode == 403) {
                                    mAccountManager.invalidateAuthToken(mAuthToken);
                                    mAuthToken = null;
                                    SharedPreferences.Editor editor = mSettings.edit();
                                    editor.remove(PREF_AUTH_TOKEN);
                                    editor.commit();
                                    Log.d(TAG, "got a 403");
                                    return false;
                                }
                                return false;
                            }
                    };
                }
        });
        haveAccount();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String file = extras.getString(EXTRA_FILE);
            if (file != null) new UploadFilesTask().execute(file);
        }

    }

    /**
     * Lets the user choose which Google provided account to upload to
     */
    private void haveAccount() {
        Account account = mAccountManager.getAccountByName(mAccountName);

        if (account != null) {
            if (mAuthToken == null) {
                mAccountManager.manager.getAuthToken(account, AUTH_TOKEN_TYPE, true,
                        new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> arg0) {
                        Bundle bundle;
                        try {
                            bundle = arg0.getResult();

                            if (bundle.containsKey(AccountManager.KEY_INTENT)) {
                                Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
                                int flags = intent.getFlags();
                                flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
                                intent.setFlags(flags);
                                startActivityForResult(intent, REQUEST_AUTHENTICATE);
                            } else if (bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
                                setAuthToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));
                                //
                            }
                        } catch (OperationCanceledException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (AuthenticatorException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }, null);
            } else {
            //
            }
            return;
        }
        chooseAccount();
    }

    /**
     * Manages the authToken for the chosen Google account
     */
    private void chooseAccount() {
        AccountManager.get(this).getAuthTokenByFeatures(AUTH_ACCOUNT, AUTH_TOKEN_TYPE, null, this,
                null, null, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> arg0) {
                Bundle bundle;
                try {
                    bundle = arg0.getResult();

                    setAccountName(bundle.getString(AccountManager.KEY_ACCOUNT_NAME));
                    setAuthToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));
                } catch (OperationCanceledException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (AuthenticatorException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO
                }
            }
        }, null);
    }

    /**
     * Updates the Persistent Settings with the provided auth token
     * @param authToken
     */
    void setAuthToken(String authToken) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(PREF_AUTH_TOKEN, authToken);
        editor.commit();
        this.mAuthToken = authToken;
    }

    /**
     * Updates the Persistent Settings with the provided account name
     * @param accountName
     */
    void setAccountName(String accountName) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(PREF_ACCOUNT_NAME, accountName);
        editor.commit();
        this.mAccountName = accountName;
    }

    /**
     * Displays a Progress Dialog to the user while an upload is taking place
     */
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch (id) {
            case DIALOG_UPLOADING:
                dialog = ProgressDialog.show(PicasaUploadActivity.this, "",
                        getResources().getString(R.string.dialog_uploading), true);
                break;
            default:
                dialog = null;
                break;
        }
        return dialog;
    }

    /**
     * Uploads a file a file to Picasa via different thread
     */
    private class UploadFilesTask extends AsyncTask<String, Void, Integer> {

        /**
         * Show a Progress Dialog to the user
         */
        @Override
        protected void onPreExecute() {
            showDialog(DIALOG_UPLOADING);
        }

        /**
         * Uploads the files to Picasa
         */
        @Override
        protected Integer doInBackground(String... files) {
            for (int i = 0; i < files.length; i++) {
                try {
                    mAuthToken = mSettings.getString(PREF_AUTH_TOKEN, null);
                    mAccountName = mSettings.getString(PREF_ACCOUNT_NAME, null);

                    File file = new File(files[i]);

                    Uri uri = Uri.parse("file:/" + file.getPath());
                    InputStreamContent content = new InputStreamContent();
                    ContentResolver contentResolver = getContentResolver();

                    content.inputStream = contentResolver.openInputStream(uri);

                    content.type = PanoActivity.MIME_TYPE;
                    content.length = file.length();

                    HttpRequest request = mRequestFactory.buildPostRequest(new GenericUrl(
                            "https://picasaweb.google.com/data/feed/api/user/default/albumid/default"),
                            content);
                    ((GoogleHeaders) request.headers).setSlugFromFileName(files[i]);

                    Log.d(TAG, "saving to picasa");
                    request.enableGZipContent = true;
                    request.execute().ignore();

                } catch (FileNotFoundException e) {
                    return Activity.RESULT_CANCELED;
                } catch (HttpResponseException e) {
                    return e.response.statusCode;
                } catch (IOException e) {
                    return Activity.RESULT_FIRST_USER;
                }
            }
            return Activity.RESULT_OK;
        }

        /**
         * Dismisses the Progress Dialog, generates the result code of successful image upload
         * and returns to the calling Activity.
         */
        protected void onPostExecute(Integer result) {
            Log.d(TAG, "Result: " + result);
            switch (result) {
                case Activity.RESULT_OK:
                    setResult(Activity.RESULT_OK, new Intent());
                    break;
                case Activity.RESULT_CANCELED:
                    setResult(Activity.RESULT_CANCELED, new Intent());
                    break;
                case Activity.RESULT_FIRST_USER:
                    setResult(Activity.RESULT_FIRST_USER, new Intent());
                    break;
                default:
                    setResult(result, new Intent());
                    break;
            }
            dismissDialog(DIALOG_UPLOADING);
            finish();
        }
    }
}
