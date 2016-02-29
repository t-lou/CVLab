package com.casuals.tlou.cvlab.imgproc;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.gui.GridForFiles;
import com.casuals.tlou.cvlab.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/*
 *
 *  * Copyright 2016 Tongxi Lou
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

public class Gallerie extends Activity implements View.OnClickListener {
    private GridForFiles gridview_items;

    private Button button_set_dir_home;
    private Button button_set_dir_sdcard;
    private Button button_set_dir_internal;
    private Button button_set_sel_mode;
    private Button button_back;

    private TextView textview_current_dir;

    private boolean openDir(String path) {
        if(this.gridview_items.openDir(path)) {
            this.textview_current_dir.setText(this.gridview_items.getCurrentDir());
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallerie);

        this.button_set_dir_home = (Button)findViewById(R.id.button_gallery_warehouse);
        this.button_set_dir_sdcard = (Button)findViewById(R.id.button_gallery_sdcard);
        this.button_set_dir_internal = (Button)findViewById(R.id.button_gallery_internal);
        this.button_set_sel_mode = (Button)findViewById(R.id.button_gallery_selectitem);
        this.button_back = (Button)findViewById(R.id.button_gallery_back);

        this.button_set_dir_home.setOnClickListener(this);
        this.button_set_dir_sdcard.setOnClickListener(this);
        this.button_set_dir_internal.setOnClickListener(this);
        this.button_set_sel_mode.setOnClickListener(this);
        this.button_back.setOnClickListener(this);

        this.textview_current_dir = (TextView)findViewById(R.id.textview_gallerie_current_dir);
        this.gridview_items = new GridForFiles(this, (GridView)findViewById(R.id.gridview_gallerie)) {
            @Override
            public void onClickImage(String path) {
                Intent in = new Intent(getApplicationContext(), SwissKnife.class);
                in.putExtra("path", path);
                startActivity(in);
            }
        };
        this.textview_current_dir.setText(this.gridview_items.getCurrentDir());
    }

    @Override
    public void onClick(View v) {
        Intent in;

        switch (v.getId()) {
            case R.id.button_gallery_back:
                if(this.gridview_items.ifSelectMode()) {
                    this.gridview_items.getSelectedItems().clear();
                }
                else {
                    in = new Intent(this, main.class);
                    startActivity(in);
                }
                break;

            case R.id.button_gallery_selectitem:
                this.gridview_items.invSelectMode();
                if(this.gridview_items.ifSelectMode()) {
                    this.button_set_sel_mode.setText("Copy");
                    this.button_back.setText("Unsel");
                }
                else {
                    this.button_set_sel_mode.setText("Sel");
                    this.button_back.setText("Back");

                    for(String str: this.gridview_items.getSelectedItems()) {
                        File src = new File(str);
                        if(src.isDirectory()) continue;

                        String filename = src.getName();
                        File dst = new File(Environment.getExternalStorageDirectory()
                                + getString(R.string.workshop_dir) + "/" + filename);

                        try {
                            InputStream input_stream = new FileInputStream(src);
                            OutputStream output_stream = new FileOutputStream(dst);
                            long size = src.length();
                            byte[] bytes = new byte[(int)size];
                            input_stream.read(bytes);
                            output_stream.write(bytes);
                            output_stream.close();
                            input_stream.close();
                        } catch(FileNotFoundException e) {
                            this.gridview_items.alert(str + " not found(Internal error)");
                        } catch(IOException e) {
                            this.gridview_items.alert(str + " failed(Internal error)");
                        }
                    }
                }
                this.gridview_items.getSelectedItems().clear();
                break;

            case R.id.button_gallery_internal:
                if(this.gridview_items.getCurrentDir().compareTo(System.getenv("EXTERNAL_STORAGE")) != 0) {
                    if (!this.openDir(System.getenv("EXTERNAL_STORAGE"))) {
                        this.gridview_items.alert("Internal storage not found");
                    }
                }
                break;

            case R.id.button_gallery_sdcard:
                if(this.gridview_items.getCurrentDir().compareTo(System.getenv("SECONDARY_STORAGE")) != 0) {
                    if (!this.openDir(System.getenv("SECONDARY_STORAGE"))) {
                        this.gridview_items.alert("SD card storage not found");
                    }
                }
                break;

            case R.id.button_gallery_warehouse:
                if(!this.openDir(System.getenv("SECONDARY_STORAGE") + getString(R.string.img_dir))) {
                    if(!this.openDir(System.getenv("EXTERNAL_STORAGE") + getString(R.string.img_dir))) {
                        this.gridview_items.alert("Directory not found");
                    }
                }
                break;
        }

        if(this.gridview_items.getCurrentDir().length() > 0) {
            this.openDir(this.gridview_items.getCurrentDir());
            this.gridview_items.updateView();
        }
    }
}
