package com.oem.usbserialtest;

import android.os.Bundle;
import android.widget.TextView;

public class WritingTestSeparated extends BaseTest {
    private TextView mTvProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTestMode = TestManager.MODE_WRITE_SEPARATED;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_writing_test_separated);
        mTvProgress = findViewById(R.id.tv_writing_separated_progress);
    }

    @Override
    protected void showTestProgress(final String progress) {
        mTvProgress.setText(progress);
    }
}