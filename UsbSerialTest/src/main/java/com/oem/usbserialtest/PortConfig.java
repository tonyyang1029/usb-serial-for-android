package com.oem.usbserialtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;

public class PortConfig extends AppCompatActivity implements AdapterView.OnItemSelectedListener, View.OnClickListener {
    private Spinner mSpinnerPort;
    private Spinner mSpinnerBaudRate;
    private Spinner mSpinnerDataBits;
    private Spinner mSpinnerStopBits;
    private Spinner mSpinnerParityType;

    private Button mBtnOk;
    private Button mBtnCancel;

    private SharedPreferences mPref;
    private SharedPreferences.Editor mPrefEditor;
    private int     mPortVid;
    private int     mPortPid;
    private int     mPortNum;
    private String  mPortDriver;
    private int     mBaudRate;
    private int     mDataBits;
    private int     mStopBits;
    private int     mParityType;

    private ArrayList<PortInfo> mPortsList;
    private ArrayAdapter<PortInfo> mPortsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_port_config);

        initPref();
        int i = 0;

        mSpinnerPort = findViewById(R.id.spinner_port);
        //
        mSpinnerBaudRate = findViewById(R.id.spinner_baudrate);
        if (mBaudRate == -1) {
            i = 4;
        } else {
            int[] baudrates = getResources().getIntArray(R.array.baudrate_vals);
            for (i = 0; i < baudrates.length; i++) {
                if (baudrates[i] == mBaudRate) {
                    break;
                }
            }
        }
        mSpinnerBaudRate.setSelection(i);
        mSpinnerBaudRate.setOnItemSelectedListener(this);
        //
        mSpinnerDataBits = findViewById(R.id.spinner_databits);
        if (mDataBits == -1) {
            i = 3;
        } else {
            int[] databits = getResources().getIntArray(R.array.databits_vals);
            for (i = 0; i < databits.length; i++) {
                if (databits[i] == mDataBits) {
                    break;
                }
            }
        }
        mSpinnerDataBits.setSelection(i);
        mSpinnerDataBits.setOnItemSelectedListener(this);
        //
        mSpinnerStopBits = findViewById(R.id.spinner_stopbits);
        if (mStopBits == -1) {
            i = 0;
        } else {
            int[] stopbits = getResources().getIntArray(R.array.stopbits_vals);
            for (i = 0; i < stopbits.length; i++) {
                if (stopbits[i] == mStopBits) {
                    break;
                }
            }
        }
        mSpinnerStopBits.setSelection(i);
        mSpinnerStopBits.setOnItemSelectedListener(this);
        //
        mSpinnerParityType = findViewById(R.id.spinner_paritytype);
        if (mParityType == -1) {
            i = 0;
        } else {
            int[] paritytypes = getResources().getIntArray(R.array.paritytype_vals);
            for (i = 0; i < paritytypes.length; i++) {
                if (paritytypes[i] == mParityType) {
                    break;
                }
            }
        }
        mSpinnerParityType.setSelection(i);
        mSpinnerParityType.setOnItemSelectedListener(this);

        mBtnOk = findViewById(R.id.btn_port_ok);
        mBtnOk.setOnClickListener(this);
        //
        mBtnCancel = findViewById(R.id.btn_port_cancel);
        mBtnCancel.setOnClickListener(this);

        mPortsList = new ArrayList<>();
        mPortsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mPortsList);
        mPortsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        listPorts();
        if (mPortsList.size() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Error");
            builder.setMessage("No USB serial devices found.");
            builder.setPositiveButton("OK", null);
        } else {
            mSpinnerPort.setAdapter(mPortsAdapter);
            mSpinnerPort.setOnItemSelectedListener(this);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.spinner_port:
                PortInfo portInfo = mPortsList.get(position);
                mPrefEditor.putInt("vid", portInfo.VID);
                mPrefEditor.putInt("pid", portInfo.PID);
                mPrefEditor.putInt("num", portInfo.NUM);
                mPrefEditor.putString("driver", portInfo.DRIVER);
                break;

            case R.id.spinner_baudrate:
                mBaudRate = getResources().getIntArray(R.array.baudrate_vals)[position];
                mPrefEditor.putInt("baudrate", mBaudRate);
                break;

            case R.id.spinner_databits:
                mDataBits = getResources().getIntArray(R.array.databits_vals)[position];
                mPrefEditor.putInt("databits", mDataBits);
                break;

            case R.id.spinner_stopbits:
                mStopBits = getResources().getIntArray(R.array.stopbits_vals)[position];
                mPrefEditor.putInt("stopbits", mStopBits);
                break;

            case R.id.spinner_paritytype:
                mParityType = getResources().getIntArray(R.array.paritytype_vals)[position];
                mPrefEditor.putInt("paritytype", mParityType);
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private void initPref() {
        mPref = getSharedPreferences("com.oem.usbserialloopback_preferences", MODE_PRIVATE);
        mPrefEditor = mPref.edit();
        mPortVid    = mPref.getInt("vid", -1);
        mPortPid    = mPref.getInt("pid", -1);
        mPortNum    = mPref.getInt("num", -1);
        mPortDriver = mPref.getString("driver", "");
        mBaudRate   = mPref.getInt("baudrate", -1);
        mDataBits   = mPref.getInt("databits", -1);
        mStopBits   = mPref.getInt("stopbits", -1);
        mParityType = mPref.getInt("paritytype", -1);
    }

    private void listPorts() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbSerialProber = UsbSerialProber.getDefaultProber();
        mPortsList.clear();

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver usbDriver = usbSerialProber.probeDevice(device);
            if (usbDriver != null) {
                for (int i = 0; i < usbDriver.getPorts().size(); i++) {
                    mPortsList.add(new PortInfo(device.getVendorId(), device.getProductId(), i, usbDriver.getClass().getSimpleName().replace("SerialDriver", "")));
                }
            }
        }

        mPortsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_port_ok:
                mPrefEditor.commit();
                finish();
                break;

            case R.id.btn_port_cancel:
                finish();
                break;
        }
    }

    class PortInfo {
        public int VID;
        public int PID;
        public int NUM;
        public String DRIVER;

        public PortInfo() {
            VID = -1;
            PID = -1;
            NUM = -1;
            DRIVER = null;
        }
        public PortInfo(int vid, int pid, int num, String driver) {
            VID = vid;
            PID = pid;
            NUM = num;
            DRIVER = driver;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("Vendor: %04X, Product: %04X, Port: %d, Driver: %s", VID, PID, NUM, DRIVER);
        }
    }
}