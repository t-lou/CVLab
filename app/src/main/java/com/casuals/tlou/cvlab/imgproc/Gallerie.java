package com.casuals.tlou.cvlab.imgproc;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

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

    private class GridViewAdapter extends ArrayAdapter {
        private class ViewHolder {
            TextView text;
            ImageView image;
        }

        private Context context;
        private int resource;
        private ArrayList data;

        public GridViewAdapter(Context context, int resource, ArrayList data) {
            super(context, resource, data);
            this.context = context;
            this.resource = resource;
            this.data = data;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            ViewHolder holder = null;

            if (row == null) {
                LayoutInflater inflater = ((Activity) context).getLayoutInflater();
                row = inflater.inflate(this.resource, parent, false);
                holder = new ViewHolder();
                holder.text = (TextView)row.findViewById(R.id.gallerie_item_text);
                holder.image = (ImageView)row.findViewById(R.id.gallerie_item_image);
                row.setTag(holder);
            } else {
                holder = (ViewHolder)row.getTag();
            }

            GallerieItem item = (GallerieItem)data.get(position);
            holder.text.setText(item.getName());
            holder.image.setImageBitmap(item.getSymbol());
            return row;
        }
    }

    private GridView gridview_items;
    private GridViewAdapter gridview_items_adapter;

    private Button button_set_dir_home;
    private Button button_set_dir_sdcard;
    private Button button_set_dir_internal;
    private Button button_set_dir_back;

    private TextView textview_current_dir;

    private String current_dir;
    private String[] entries;
    private Bitmap icon_file;
    private Bitmap icon_dir;

    private ArrayList<GallerieItem> getData() {
        final ArrayList<GallerieItem> image_items = new ArrayList<>();
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        File file;
        String suffix;

        if(this.current_dir.length() > 0 && this.entries != null) {
            bitmap = this.icon_dir.copy(conf, true);
            image_items.add(new GallerieItem(bitmap, ".."));

            for (String str : this.entries) {
                file = new File(this.current_dir + "/" + str);
                if(!file.exists()) {
                    continue;
                }

                if(file.isDirectory()) {
                    bitmap = this.icon_dir.copy(conf, true);
                }
                else if(str.indexOf('.') < 0) {
                    bitmap = this.icon_file.copy(conf, true);
                }
                else {
                    suffix = str.substring(str.lastIndexOf('.')).toLowerCase();
                    if(suffix.compareTo(".png") == 0
                            || suffix.compareTo(".jpeg") == 0
                            || suffix.compareTo(".jpg") == 0
                            || suffix.compareTo(".dng") == 0
                            || suffix.compareTo(".tiff") == 0) {
                        bitmap = ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeFile(file.getAbsolutePath()), 100, 100);
                    }
                    else {
                        bitmap = this.icon_file.copy(conf, true);
                    }
                }
                image_items.add(new GallerieItem(bitmap, str));
            }
        }
        return image_items;
    }

    private boolean openDir(String name) {
        File file = new File(name);
        boolean result = false;
        if(file.exists()) {
            if(file.isDirectory()) {
                result = true;
            }
            else {
                result = false;
            }
        }
        else {
            result = false;
        }

        if(result) {
            try {
                this.current_dir = file.getCanonicalPath();
            } catch (IOException e) {
                this.current_dir = file.getAbsolutePath();
            }
            this.entries = file.list();
        }
        else {
            this.current_dir = "";
            this.entries = null;
        }
        this.textview_current_dir.setText(this.current_dir);
        return result;
    }

    private void alert(String message) {
        AlertDialog.Builder alert;

        alert = new AlertDialog.Builder(this);
        alert.setMessage(message)
                .setCancelable(true);
        alert.show();
    }

    private void selectItem(int i) {
        GallerieItem item = (GallerieItem)this.gridview_items.getItemAtPosition(i);
        String name = item.getName();
        File file = new File(this.current_dir + "/" + name);
        if(file.isDirectory()) {
            this.current_dir += ("/" + name);
            this.openDir(this.current_dir);
            this.updateView();
        }
        else if(name.indexOf('.') < 0) {
            this.alert("Cannot find suffix of file");
        }
        else {
            String suffix = name.substring(name.lastIndexOf('.')).toLowerCase();
            if(suffix.compareTo(".png") == 0
                    || suffix.compareTo(".jpeg") == 0
                    || suffix.compareTo(".jpg") == 0
                    || suffix.compareTo(".dng") == 0
                    || suffix.compareTo(".tiff") == 0) {
                Intent in = new Intent(this, SwissKnife.class);
                in.putExtra("path", file.getAbsolutePath());
                startActivity(in);
            }
            else {
                this.alert("Not supported image file\n(png, jpg, dng, tiff)");
            }
        }
    }

    private void updateView() {
        this.gridview_items_adapter = new GridViewAdapter(this, R.layout.gallerie_item, this.getData());
        this.gridview_items.setAdapter(this.gridview_items_adapter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallerie);

        this.button_set_dir_home = (Button)findViewById(R.id.button_gallery_warehouse);
        this.button_set_dir_sdcard = (Button)findViewById(R.id.button_gallery_sdcard);
        this.button_set_dir_internal = (Button)findViewById(R.id.button_gallery_internal);
        this.button_set_dir_back = (Button)findViewById(R.id.button_gallery_back);

        this.button_set_dir_home.setOnClickListener(this);
        this.button_set_dir_sdcard.setOnClickListener(this);
        this.button_set_dir_internal.setOnClickListener(this);
        this.button_set_dir_back.setOnClickListener(this);

        this.textview_current_dir = (TextView)findViewById(R.id.textview_gallerie_current_dir);

        this.openDir(System.getenv("EXTERNAL_STORAGE"));
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        this.icon_dir = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(getResources(), R.drawable.gallerie_dir_icon), 100, 100);
        this.icon_file = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(getResources(), R.drawable.gallerie_file_icon), 100, 100);

        this.gridview_items = (GridView)findViewById(R.id.gridview_gallerie);
        this.updateView();
        this.gridview_items.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> gridview, View v, int i, long l) {
                        selectItem(i);
                    }
                }
        );
    }

    @Override
    public void onClick(View v) {
        Intent in;

        switch (v.getId()) {
            case R.id.button_gallery_back:
                in = new Intent(this, main.class);
                startActivity(in);
                break;

            case R.id.button_gallery_internal:
                if(this.current_dir.compareTo(System.getenv("EXTERNAL_STORAGE")) != 0) {
                    if (!this.openDir(System.getenv("EXTERNAL_STORAGE"))) {
                        this.alert("Internal storage not found");
                    }
                }
                break;

            case R.id.button_gallery_sdcard:
                if(this.current_dir.compareTo(System.getenv("SECONDARY_STORAGE")) != 0) {
                    if (!this.openDir(System.getenv("SECONDARY_STORAGE"))) {
                        this.alert("SD card storage not found");
                    }
                }
                break;

            case R.id.button_gallery_warehouse:
                if(!this.openDir(System.getenv("SECONDARY_STORAGE") + getString(R.string.img_dir))) {
                    if(!this.openDir(System.getenv("EXTERNAL_STORAGE") + getString(R.string.img_dir))) {
                        this.alert("Directory not found");
                    }
                }
                break;
        }

        if(this.current_dir.length() > 0) {
            this.openDir(this.current_dir);
            this.updateView();
        }
    }
}
