package com.oem.usbserialtest;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Button mBtnPortConfig;
    private Button mBtnTestDataConfig;
    private Button mBtnWritingTest;
    private Button mBtnWritingTestIom;
    private Button mBtnWritingTestSeparated;
    private Button mBtnReadingTest;
    private Button mBtnReadingTestIom;
    private Button mBtnLoopbackTest;
    private Button mBtnLoopbackTestSeparated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBtnPortConfig = findViewById(R.id.btn_main_port_config);
        mBtnPortConfig.setOnClickListener(this);
        //
        mBtnTestDataConfig = findViewById(R.id.btn_main_test_data_config);
        mBtnTestDataConfig.setOnClickListener(this);
        //
        mBtnWritingTest = findViewById(R.id.btn_main_writing);
        mBtnWritingTest.setOnClickListener(this);
        //
        mBtnWritingTestIom = findViewById(R.id.btn_main_writing_iom);
        mBtnWritingTestIom.setOnClickListener(this);
        //
        mBtnWritingTestSeparated = findViewById(R.id.btn_main_writing_separated);
        mBtnWritingTestSeparated.setOnClickListener(this);
        //
        mBtnReadingTest = findViewById(R.id.btn_main_reading);
        mBtnReadingTest.setOnClickListener(this);
        //
        mBtnReadingTestIom = findViewById(R.id.btn_main_reading_iom);
        mBtnReadingTestIom.setOnClickListener(this);
        //
        mBtnLoopbackTest = findViewById(R.id.btn_main_loopback);
        mBtnLoopbackTest.setOnClickListener(this);
        //
        mBtnLoopbackTestSeparated = findViewById(R.id.btn_main_loopback_separated);
        mBtnLoopbackTestSeparated.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_main_port_config:
                startActivity(new Intent(MainActivity.this, PortConfig.class));
                break;

            case R.id.btn_main_test_data_config:
                startActivity(new Intent(MainActivity.this, DataConfig.class));
                break;

            case R.id.btn_main_writing:
                startActivity(new Intent(MainActivity.this, WritingTest.class));
                break;

            case R.id.btn_main_writing_iom:
                startActivity(new Intent(MainActivity.this, WritingTestIom.class));
                break;

            case R.id.btn_main_writing_separated:
                startActivity(new Intent(MainActivity.this, WritingTestSeparated.class));
                break;

            case R.id.btn_main_reading:
                startActivity(new Intent(MainActivity.this, ReadingTest.class));
                break;

            case R.id.btn_main_reading_iom:
                startActivity(new Intent(MainActivity.this, ReadingTestIom.class));
                break;

            case R.id.btn_main_loopback:
                startActivity(new Intent(MainActivity.this, LoopbackTest.class));
                break;

            case R.id.btn_main_loopback_separated:
                startActivity(new Intent(MainActivity.this, LoopbackTestSeparated.class));
                break;
        }
    }
}