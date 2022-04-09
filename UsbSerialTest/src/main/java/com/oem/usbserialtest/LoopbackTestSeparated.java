package com.oem.usbserialtest;

import android.os.Bundle;
import android.widget.TextView;

public class LoopbackTestSeparated extends BaseTest {
    private TextView mProgress;
    private String mWriteProgress;
    private String mReadProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTestMode = TestManager.MODE_LOOPBACK_SEPARATED;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loopback_test_separated);
        mProgress = findViewById(R.id.tv_loopback_separated_progress);
    }

    @Override
    protected void showTestProgress(final String progress) {
        StringBuffer buffer = new StringBuffer();
        String other = null;

        if (progress.contains("sent")) {
            mWriteProgress = progress;
        } else if (progress.contains("received")) {
            mReadProgress = progress;
        } else {
            other = progress;
        }

        if (other != null) {
            buffer.append(other);
            buffer.append("\n");
        }
        if (mWriteProgress != null) {
            buffer.append(mWriteProgress);
            buffer.append("\n");
        }
        if (mReadProgress != null) {
            buffer.append(mReadProgress);
        }

        mProgress.setText(buffer.toString());
    }
}