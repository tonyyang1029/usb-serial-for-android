package com.oem.usbserialtest;

import android.os.Bundle;
import android.widget.TextView;

public class ReadingTestIom extends BaseTest {
    private TextView mTvProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTestMode = TestManager.MODE_READ_IOM;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_test_iom);
        mTvProgress = findViewById(R.id.tv_reading_iom_progress);
    }

    @Override
    protected void showTestProgress(final String progress) {
        mTvProgress.setText(progress);
    }
}