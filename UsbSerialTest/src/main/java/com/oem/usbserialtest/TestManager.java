package com.oem.usbserialtest;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TestManager implements SerialInputOutputManager.Listener{
    public static final String          ACTION_USB_PERMISSION_REQUEST = "com.oem.usbserialtest.action.USB_PERMISSION_REQUEST";
    //
    public static final int             MODE_UNKNOWN            = 0;
    public static final int             MODE_WRITE              = 10;
    public static final int             MODE_WRITE_IOM          = 11;
    public static final int             MODE_WRITE_SEPARATED    = 12;
    public static final int             MODE_READ               = 20;
    public static final int             MODE_READ_IOM           = 21;
    public static final int             MODE_LOOPBACK           = 30;
    public static final int             MODE_LOOPBACK_SEPARATED = 31;
    //
    public static final int             MSG_ERROR_INFO          = 10;
    public static final int             MSG_TEST_PROGRESS       = 20;

    private final long                  MAX_PACKET_COUNT        = 20000;// TODO: 5000;
    private final int                   INTERVAL_WRITE          = 2000;
    private final int                   INTERVAL_WRITE_SECTION  = 100;
    private final int                   INTERVAL_READ           = 1;
    private final int                   TIMEOUT_WRITE           = 300;// TODO: 500;
    private final int                   TIMEOUT_READ            = 300;// TODO: 500;
    private final int                   READ_BUFFER_SIZE        = 1024;
    private final String                TAG;
    private final Context               mCtxt;
    private final UsbManager            mUsbManager;
    private final Handler               mUiHandler;
    private final Handler               mTestHandler;
    private final int                   mTestMode;
    //
    private int                         mPortVid;
    private int                         mPortPid;
    private int                         mPortNum;
    private String                      mPortDriver;
    private int                         mBaudRate;
    private int                         mDataBits;
    private int                         mStopBits;
    private int                         mParityType;
    private String                      mTestDataFile;
    //
    private UsbDevice                   mDevice;
    private UsbSerialDriver             mDriver;
    private UsbSerialPort               mPort;
    private SerialInputOutputManager    mSerialIoManager;
    private byte[]                      mTestData;
    private File                        mResultFile;
    //
    private boolean                     isRunning;
    private long                        mWriteCount;
    private int                         mWriteSize;
    private long                        mReadCount;
    private int                         mReadSize;
    //
    private Object                      mLock;

    public TestManager(Context ctxt, UsbManager usbManager, Handler uiHandler, Handler testHandler, int testMode) {
        TAG = Application.TAG;
        //
        mCtxt = ctxt;
        mUsbManager = usbManager;
        mUiHandler = uiHandler;
        mTestHandler = testHandler;
        mTestMode = testMode;
        //
        mLock = new Object();
    }

    public void init() {
        mDevice = null;
        mDriver = null;
        mPort = null;
        mTestData = null;
        mResultFile = null;
        //
        isRunning = false;
        mWriteCount = 0;
        mWriteSize = 0;
        mReadCount = 0;
        mReadSize = 0;
        //
        readPreference();
    }

    private void readPreference() {
        SharedPreferences pref = mCtxt.getSharedPreferences("com.oem.usbserialloopback_preferences", Context.MODE_PRIVATE);
        mPortVid      = pref.getInt("vid", -1);
        mPortPid      = pref.getInt("pid", -1);
        mPortNum      = pref.getInt("num", -1);
        mPortDriver   = pref.getString("driver", "");
        mBaudRate     = pref.getInt("baudrate", -1);
        mDataBits     = pref.getInt("databits", -1);
        mStopBits     = pref.getInt("stopbits", -1);
        mParityType   = pref.getInt("paritytype", -1);
        mTestDataFile = pref.getString("datafile", "");
    }

    public void start() {
        synchronized (mLock) {
            if (isRunning) return;

            importTestData();
            if (mTestData == null) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Test data is empty."));
                return;
            }

            createResultFile();
            if (mResultFile == null) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Result file is not created."));
                return;
            }

            for (UsbDevice device : mUsbManager.getDeviceList().values()) {
                if (device.getVendorId() == mPortVid && device.getProductId() == mPortPid) {
                    if (!mUsbManager.hasPermission(device)) {
                        PendingIntent pi = PendingIntent.getBroadcast(mCtxt, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0);
                        mUsbManager.requestPermission(device, pi);
                        return;
                    }

                    mDevice = device;
                    break;
                }
            }

            if (mDevice == null) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Serial device is not found."));
                return;
            }
            //
            mDriver = UsbSerialProber.getDefaultProber().probeDevice(mDevice);
            if (mDriver == null) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Serial drive is not found."));
                return;
            }
            //
            mPort = mDriver.getPorts().get(mPortNum);
            if (mPort == null) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Serial port is not found."));
                return;
            }
            //
            UsbDeviceConnection deviceConnection = mUsbManager.openDevice(mDriver.getDevice());
            if (deviceConnection == null) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Serial port cannot be connected."));
                return;
            }
            //
            try {
                mPort.open(deviceConnection);
                mPort.setParameters(mBaudRate, mDataBits, mStopBits, mParityType);
            } catch (IOException e) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The serial port cannot be opened."));
                return;
            }

            switch (mTestMode) {
                case MODE_WRITE:
                    doWritingTest();
                    break;

                case MODE_WRITE_IOM:
                    doWritingTestIom();
                    break;

                case MODE_WRITE_SEPARATED:
                    doWritingTestSeparated();
                    break;

                case MODE_READ:
                    doReadingTest();
                    break;

                case MODE_READ_IOM:
                    doReadingTestIom();
                    break;

                case MODE_LOOPBACK:
                    doLoopbackTest();
                    //doLoopbackTestThread();
                    break;

                case MODE_LOOPBACK_SEPARATED:
                    doLoopbackTestSeparated();
                    //dodoLoopbackTestSeparatedThread();
                    break;
            }
        }
    }

    public void stop() {
        synchronized (mLock) {
            if (!isRunning) return;
            isRunning = false;

            mTestHandler.removeCallbacksAndMessages(null);

            if (mSerialIoManager != null) {
                mSerialIoManager.setListener(null);
                mSerialIoManager.stop();
                mSerialIoManager = null;
            }

            mWriteCount = 0;
            mWriteSize = 0;
            mReadCount = 0;
            mReadSize = 0;

            try {
                mPort.close();
            } catch (IOException e) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "It's failed to stop testing."));
            }
            mPort = null;
            mDevice = null;
            mDriver = null;
            mTestData = null;
            mResultFile = null;
        }
    }

    public boolean isTestDevice(UsbDevice device) {
        if (mDevice == null) {
            return false;
        } else if (device == null) {
            return false;
        } else {
            return mDevice.equals(device);
        }
    }

    @Override
    public void onNewData(byte[] data) {
        synchronized (mLock) {
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mResultFile, true))) {
                bos.write(data);
                mReadSize += data.length;
                Log.i(TAG, "[READ] len = " + data.length + ", total size = " + mReadSize + ", count = " + mReadCount);

                if (mReadSize == 512) {
                    mReadCount++;
                    mReadSize = 0;
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mReadCount + " pieces of 512-byte packet received."));
                    if (mReadCount == MAX_PACKET_COUNT) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mReadCount + " pieces of 512-byte packet received."));
                        stop();
                        return;
                    }
                } else if (mReadSize > 512) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Packet size: " + mReadSize + ", it is bigger than 512 bytes."));
                    stop();
                }
            } catch (FileNotFoundException e) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file is not found."));
                stop();
            } catch (IOException e) {
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file can not be written."));
                stop();
            }
        }
    }

    @Override
    public void onRunError(Exception e) {

    }

    private void importTestData() {
        if (!mTestDataFile.equals("")) {
            File file = new File(mTestDataFile);
            if (file.length() != 512) {
                mTestData = null;
                return;
            }

            int size = (int) file.length();
            ByteBuffer buffer = ByteBuffer.allocate(size);
            byte[] data = new byte[size];
            int len = 0;
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                while ((len = bis.read(data)) > 0) {
                    buffer.put(data, 0, len);
                }
                mTestData = buffer.array();
            } catch (FileNotFoundException e) {
                mTestData = null;
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Test data file is not found."));
            } catch (IOException e) {
                mTestData = null;
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "Test data file cannot be read."));
            }
        }
    }

    private void createResultFile() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US);
        String strDate = simpleDateFormat.format(calendar.getTime());
        String filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "SerialTestResult_" + strDate + ".txt";
        mResultFile = new File(filePath);
    }

    private void doWritingTest() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;

        mTestHandler.postDelayed(new SerialWriter(), INTERVAL_WRITE);
    }

    private void doWritingTestIom() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;

        mSerialIoManager = new SerialInputOutputManager(mPort, this);
        mSerialIoManager.setWriteTimeout(TIMEOUT_WRITE);
        mSerialIoManager.setWriteBufferSize(READ_BUFFER_SIZE);
        mSerialIoManager.setReadTimeout(TIMEOUT_READ);
        mSerialIoManager.setReadBufferSize(READ_BUFFER_SIZE);
        mSerialIoManager.start();

        mTestHandler.postDelayed(new SerialWriterUsingIom(), INTERVAL_WRITE);
    }

    private void doWritingTestSeparated() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;

        mTestHandler.postDelayed(new SeparatedSerialWriter(), INTERVAL_WRITE);
    }


    private void doReadingTest() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mReadCount = 0;
        mReadSize = 0;

        mTestHandler.postDelayed(new SerialReader(), INTERVAL_READ);
    }

    private void doReadingTestIom() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mReadCount = 0;
        mReadSize = 0;

        mSerialIoManager = new SerialInputOutputManager(mPort, this);
        mSerialIoManager.setWriteTimeout(TIMEOUT_WRITE);
        mSerialIoManager.setWriteBufferSize(READ_BUFFER_SIZE);
        mSerialIoManager.setReadTimeout(TIMEOUT_READ);
        mSerialIoManager.setReadBufferSize(READ_BUFFER_SIZE);
        mSerialIoManager.start();
    }

    private void doLoopbackTest() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;
        mReadCount = 0;
        mReadSize = 0;

        mTestHandler.postDelayed(new SerialWriter(), INTERVAL_WRITE);
        mTestHandler.postDelayed(new SerialReader(), INTERVAL_READ);
    }

    private void doLoopbackTestThread() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;
        mReadCount = 0;
        mReadSize = 0;

        Thread writer = new Thread(() -> {
            while (isRunning) {
                try {
                    mPort.write(mTestData, TIMEOUT_WRITE);
                    mWriteCount++;
                    Log.i(TAG, "[WRITE] count = " + mWriteCount);
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mWriteCount + " pieces of 512-byte packet sent."));
                    if (mWriteCount == MAX_PACKET_COUNT) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mWriteCount + " pieces of 512-byte packet sent."));
                        return;
                    } else {
                        SystemClock.sleep(INTERVAL_WRITE);
                    }
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "ERROR! No." + mWriteCount + " packet cannot be sent"));
                    stop();
                    return;
                }
            }
        });
        writer.start();

        Thread reader = new Thread(() -> {
            while (isRunning) {
                byte[] data = new byte[READ_BUFFER_SIZE];
                int len;

                try {
                    len = mPort.read(data, TIMEOUT_READ);
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The port cannot be read."));
                    stop();
                    return;
                }

                if (len > 0) {
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mResultFile, true))) {
                        bos.write(data, 0, len);
                        mReadSize += len;
                        Log.i(TAG, "[READ] len = " + len + ", total size = " + mReadSize + ", count = " + mReadCount);
                    } catch (FileNotFoundException e) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file is not found."));
                        stop();
                        return;
                    } catch (IOException e) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file cannot be written."));
                        stop();
                        return;
                    }

                    if (mReadSize == 512) {
                        mReadSize = 0;
                        mReadCount++;
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mReadCount + " pieces of 512-byte packet received."));
                        if (mReadCount == MAX_PACKET_COUNT) {
                            mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mReadCount + " pieces of 512-byte packet received."));
                            stop();
                            return;
                        }
                    } else if (mReadSize > 512) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The packet size is " + mReadSize + ", it is bigger than 512 bytes."));
                        stop();
                        return;
                    }
                }
            }
        });
        reader.start();
    }

    private void doLoopbackTestSeparated() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;
        mReadCount = 0;
        mReadSize = 0;

        mTestHandler.postDelayed(new SeparatedSerialWriter(), INTERVAL_WRITE);
        mTestHandler.postDelayed(new SerialReader(), INTERVAL_READ);
    }

    private void dodoLoopbackTestSeparatedThread() {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Test is started."));
        isRunning = true;
        mWriteCount = 0;
        mWriteSize = 0;
        mReadCount = 0;
        mReadSize = 0;

        Thread writer = new Thread(() -> {
            while (isRunning) {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(128);
                    buffer.put(mTestData, mWriteSize * 128, 128);
                    mPort.write(buffer.array(), TIMEOUT_WRITE);
                    mWriteSize++;
                    Log.i(TAG, "[WRITE] size = " + mWriteSize * 128);
                    if (mWriteSize < 4) {
                        SystemClock.sleep(INTERVAL_WRITE_SECTION);
                    } else if (mWriteSize == 4) {
                        mWriteSize = 0;
                        mWriteCount++;
                        Log.i(TAG, "[WRITE] count = " + mWriteCount);
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mWriteCount + " pieces of 512-byte packet sent."));
                        if (mWriteCount == MAX_PACKET_COUNT) {
                            mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mWriteCount + " pieces of 512-byte packet sent."));
                            return;
                        } else {
                            SystemClock.sleep(INTERVAL_WRITE);
                        }
                    }
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "ERROR! No." + mWriteCount + " packet cannot be sent"));
                    stop();
                }
            }
        });
        writer.start();

        Thread reader = new Thread(() -> {
            while (isRunning) {
                byte[] data = new byte[READ_BUFFER_SIZE];
                int len;

                try {
                    len = mPort.read(data, TIMEOUT_READ);
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The port cannot be read."));
                    stop();
                    return;
                }

                if (len > 0) {
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mResultFile, true))) {
                        bos.write(data, 0, len);
                        mReadSize += len;
                        Log.i(TAG, "[READ] len = " + len + ", total size = " + mReadSize + ", count = " + mReadCount);
                    } catch (FileNotFoundException e) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file is not found."));
                        stop();
                        return;
                    } catch (IOException e) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file cannot be written."));
                        stop();
                        return;
                    }

                    if (mReadSize == 512) {
                        mReadSize = 0;
                        mReadCount++;
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mReadCount + " pieces of 512-byte packet received."));
                        if (mReadCount == MAX_PACKET_COUNT) {
                            mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mReadCount + " pieces of 512-byte packet received."));
                            stop();
                            return;
                        }
                    } else if (mReadSize > 512) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The packet size is " + mReadSize + ", it is bigger than 512 bytes."));
                        stop();
                        return;
                    }
                }
            }
        });
        reader.start();
    }

    class SerialWriter implements Runnable {
        @Override
        public void run() {
            synchronized (mLock) {
                try {
                    mPort.write(mTestData, TIMEOUT_WRITE);
                    mWriteCount++;
                    Log.i(TAG, "[WRITE] count = " + mWriteCount);
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mWriteCount + " pieces of 512-byte packet sent."));
                    if (mWriteCount == MAX_PACKET_COUNT) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mWriteCount + " pieces of 512-byte packet sent."));
                        if (mTestMode == MODE_WRITE) {
                            SystemClock.sleep(INTERVAL_WRITE);
                            stop();
                        }
                    } else {
                        mTestHandler.postDelayed(new SerialWriter(), INTERVAL_WRITE);
                    }
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "ERROR! No." + mWriteCount + " packet cannot be sent"));
                    stop();
                }
            }
        }
    }

    class SeparatedSerialWriter implements Runnable {
        @Override
        public void run() {
            synchronized (mLock) {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(128);
                    buffer.put(mTestData, mWriteSize * 128, 128);
                    mPort.write(buffer.array(), TIMEOUT_WRITE);
                    mWriteSize++;
                    Log.i(TAG, "[WRITE] size = " + mWriteSize * 128);
                    if (mWriteSize < 4) {
                        mTestHandler.postDelayed(new SeparatedSerialWriter(), INTERVAL_WRITE_SECTION);
                    } else if (mWriteSize == 4) {
                        mWriteSize = 0;
                        mWriteCount++;
                        Log.i(TAG, "[WRITE] count = " + mWriteCount);
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mWriteCount + " pieces of 512-byte packet sent."));
                        if (mWriteCount == MAX_PACKET_COUNT) {
                            mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mWriteCount + " pieces of 512-byte packet sent."));
                            if (mTestMode == MODE_WRITE_SEPARATED) {
                                SystemClock.sleep(INTERVAL_WRITE);
                                stop();
                            }
                        } else {
                            mTestHandler.postDelayed(new SeparatedSerialWriter(), INTERVAL_WRITE);
                        }
                    }
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "ERROR! No." + mWriteCount + " packet cannot be sent"));
                    stop();
                }
            }
        }
    }

    class SerialWriterUsingIom implements Runnable {
        @Override
        public void run() {
            synchronized (mLock) {
                mSerialIoManager.writeAsync(mTestData);
                mWriteCount++;
                Log.i(TAG, "[WRITE] count = " + mWriteCount);
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mWriteCount + " pieces of 512-byte packet sent."));
                if (mWriteCount == MAX_PACKET_COUNT) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mWriteCount + " pieces of 512-byte packet sent."));
                    if (mTestMode == MODE_WRITE_IOM) {
                        SystemClock.sleep(INTERVAL_WRITE);
                        stop();
                    }
                } else {
                    mTestHandler.postDelayed(new SerialWriterUsingIom(), INTERVAL_WRITE);
                }
            }
        }
    }

    class SerialReader implements Runnable {
        @Override
        public void run() {
            synchronized (mLock) {
                byte[] data = new byte[READ_BUFFER_SIZE];
                int len;

                try {
                    len = mPort.read(data, TIMEOUT_READ);
                } catch (IOException e) {
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The port cannot be read."));
                    stop();
                    return;
                }

                if (len > 0) {
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mResultFile, true))) {
                        bos.write(data, 0, len);
                        mReadSize += len;
                        Log.i(TAG, "[READ] len = " + len + ", total size = " + mReadSize + ", count = " + mReadCount);
                    } catch (FileNotFoundException e) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file is not found."));
                        stop();
                        return;
                    } catch (IOException e) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The result file cannot be written."));
                        stop();
                        return;
                    }

                    if (mReadSize == 512) {
                        mReadSize = 0;
                        mReadCount++;
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Total " + mReadCount + " pieces of 512-byte packet received."));
                        if (mReadCount == MAX_PACKET_COUNT) {
                            mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_TEST_PROGRESS, "Finish! Total " + mReadCount + " pieces of 512-byte packet received."));
                            stop();
                            return;
                        }
                    } else if (mReadSize > 512) {
                        mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_ERROR_INFO, "The packet size is " + mReadSize + ", it is bigger than 512 bytes."));
                        stop();
                        return;
                    }
                }

                if (isRunning) mTestHandler.postDelayed(new SerialReader(), INTERVAL_READ);
            }
        }
    }
}
