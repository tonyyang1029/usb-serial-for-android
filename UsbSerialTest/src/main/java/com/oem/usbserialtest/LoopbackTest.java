package com.oem.usbserialtest;

import android.os.Bundle;
import android.widget.TextView;

public class LoopbackTest extends BaseTest {
    private TextView mTvProgress;
    private String mWriteProgress;
    private String mReadProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTestMode = TestManager.MODE_LOOPBACK;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loopback_test);
        mTvProgress = findViewById(R.id.tv_loopback_progress);
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

        mTvProgress.setText(buffer.toString());
    }
}