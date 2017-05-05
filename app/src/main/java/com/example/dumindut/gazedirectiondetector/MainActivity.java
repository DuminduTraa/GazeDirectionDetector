package com.example.dumindut.gazedirectiondetector;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private boolean isFrontFacing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ToggleButton camToggle = (ToggleButton) findViewById(R.id.camera_toggle_button);
        Button startButton = (Button) findViewById(R.id.start_button);

        camToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    isFrontFacing = false;
                } else {
                    // The toggle is disabled
                    isFrontFacing = true;
                }
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FaceTrackerActivity.class);
                intent.putExtra("isFrontFacing",isFrontFacing);
                startActivity(intent);

            }
        });
    }

}
