package com.oem.usbserialtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseTest extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION_REQUEST = "com.oem.UsbSerialTest.action.USB_PERMISSION_REQUEST";

    protected int           mTestMode;
    protected UsbManager    mUsbManager;
    protected UiHandler     mUiHandler;
    protected UsbReceiver   mReceiver;
    protected TestManager   mTestManager;

    protected HandlerThread mTestHandlerThread;
    protected Handler       mTestHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUsbManager = (UsbManager) getSystemService(USB_SERVICE);
        mUiHandler = new UiHandler();
        mReceiver = new UsbReceiver(BaseTest.this);

        mTestHandlerThread = new HandlerThread("TestHandlerThread");
        mTestHandlerThread.start();
        mTestHandler = new Handler(mTestHandlerThread.getLooper());

        mTestManager = new TestManager(BaseTest.this, mUsbManager, mUiHandler, mTestHandler, mTestMode);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION_REQUEST);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTestManager.init();
        mTestManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTestManager.stop();
    }

    protected abstract void showTestProgress(final String progress);

    private void showErrorAndExit(final String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(BaseTest.this);
        builder.setTitle("Error");
        builder.setMessage(error);
        builder.setPositiveButton("OK", (dialog, which) -> BaseTest.this.finish());
        builder.create().show();
    }

    class UsbReceiver extends BroadcastReceiver {
        private final Context mCtxt;

        public UsbReceiver(Context ctxt) {
            mCtxt = ctxt;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_USB_PERMISSION_REQUEST:
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        mTestManager.start();
                    } else {
                        showErrorAndExit("No permission to access serial port.");
                    }
                    break;

                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (mTestManager.isTestDevice(device)) {
                        mTestManager.stop();
                    }
                    break;
            }
        }
    }

    class UiHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case TestManager.MSG_ERROR_INFO:
                    final String error = (String) msg.obj;
                    showErrorAndExit(error);
                    break;

                case TestManager.MSG_TEST_PROGRESS:
                    final String progress = (String) msg.obj;
                    showTestProgress(progress);
                    break;
            }
        }
    }
}