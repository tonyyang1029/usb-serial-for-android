package com.oem.usbserialtest;

import android.os.Bundle;
import android.widget.TextView;

public class WritingTest extends BaseTest {
    private TextView mTvProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTestMode = TestManager.MODE_WRITE;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_writing_test);
        mTvProcess = findViewById(R.id.tv_writing_progress);
    }

    @Override
    protected void showTestProgress(final String progress) {
        mTvProcess.setText(progress);
    }
}