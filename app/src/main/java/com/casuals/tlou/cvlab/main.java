package com.casuals.tlou.cvlab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;

import java.io.File;

import com.casuals.tlou.cvlab.camera.Camera;
import com.casuals.tlou.cvlab.imgproc.Gallerie;

/*
 * Copyright 2016 Tongxi Lou
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class main extends Activity implements View.OnClickListener {

    private Button button_camera;
    private Button button_swissknife;

    private void prepDir() {
        File folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.home_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }
        folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.img_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }
        folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.cache_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.prepDir();

        this.button_camera = (Button)findViewById(R.id.button_camera);
        this.button_swissknife = (Button)findViewById(R.id.button_swissknife);

        this.button_camera.setOnClickListener(this);
        this.button_swissknife.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent in;
        switch (v.getId()) {
            case R.id.button_camera:
                in = new Intent(this, Camera.class);
                startActivity(in);
                break;

            case R.id.button_swissknife:
                in = new Intent(this, Gallerie.class);
                startActivity(in);
                break;
        }
    }
}
