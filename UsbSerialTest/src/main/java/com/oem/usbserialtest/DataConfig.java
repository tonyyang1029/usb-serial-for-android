package com.oem.usbserialtest;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class DataConfig extends AppCompatActivity implements View.OnClickListener {
    private Button      mBtnPickData;
    private Button      mBtnOk;
    private Button      mBtnCancel;
    private TextView    mTvDataFile;

    private SharedPreferences mPref;
    private SharedPreferences.Editor mPrefEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_config);

        mBtnPickData = findViewById(R.id.btn_data_pick);
        mBtnPickData.setOnClickListener(this);
        //
        mBtnOk = findViewById(R.id.btn_data_ok);
        mBtnOk.setOnClickListener(this);
        //
        mBtnCancel = findViewById(R.id.btn_data_cancel);
        mBtnCancel.setOnClickListener(this);

        mPref = getSharedPreferences("com.oem.usbserialloopback_preferences", MODE_PRIVATE);
        mPrefEditor = mPref.edit();

        mTvDataFile = findViewById(R.id.tv_data_file);
        String path = mPref.getString("datafile", "");
        if (path.equals("")) {
            mTvDataFile.setText("No data file.");
        } else {
            mTvDataFile.setText(path);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 100 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            String path = getDataPath(uri);
            if (path != null && !path.equals("")) {
                mTvDataFile.setText(path);
                mPrefEditor.putString("datafile", path);
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_data_pick:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("text/plain");
                startActivityForResult(intent, 100);
                break;

            case R.id.btn_data_ok:
                mPrefEditor.commit();
                finish();
                break;

            case R.id.btn_data_cancel:
                finish();;
                break;
        }
    }

    private String getDataPath(Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                if (isExternalStorageDocuments(uri)) {
                    String docId = DocumentsContract.getDocumentId(uri);
                    String[] segments = docId.split(":");
                    String type = segments[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        if (segments.length > 1) {
                            return Environment.getExternalStorageDirectory() + "/" + segments[1];
                        } else {
                            return Environment.getExternalStorageDirectory() + "/";
                        }
                    }
                } else if (isDownloadsDocuments(uri)) {
                    final String dlId = DocumentsContract.getDocumentId(uri);
                    if (dlId.startsWith("raw:")) {
                        return dlId.replace("raw:", "");
                    } else {
                        try {
                            final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(dlId));
                            return getDataColumn(contentUri, null, null);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                } /*else if (isMediaDocuments(uri)) {
                    final String mediaId = DocumentsContract.getDocumentId(uri);
                    final String[] split = mediaId.split(":");
                    final String type = split[0];
                    Uri contentUri = null;
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};
                    return getDataColumn(contentUri, selection, selectionArgs);
                }*/
            }
        }

        return null;
    }

    private boolean isExternalStorageDocuments(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private boolean isDownloadsDocuments(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private boolean isMediaDocuments(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }
}