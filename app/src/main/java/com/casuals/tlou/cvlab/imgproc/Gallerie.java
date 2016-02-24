package com.casuals.tlou.cvlab.imgproc;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;

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
    private Button button_set_sel_mode;
    private Button button_back;

    private TextView textview_current_dir;

    private String current_dir;
    private String[] entries;
    private Bitmap icon_file;
    private Bitmap icon_dir;
    private Bitmap icon_image_selected;
    private int width_icon;

    private boolean if_select_mode;
    private LinkedList<String> list_selected_items;

    private boolean ifImageSuffix(String suffix) {
        return suffix.compareTo(".png") == 0 || suffix.compareTo(".jpeg") == 0
                || suffix.compareTo(".jpg") == 0 || suffix.compareTo(".dng") == 0
                || suffix.compareTo(".tiff") == 0;
    }

    private String getFilePath(File file) {
        String path = "";
        try {
            path = file.getCanonicalPath();
        } catch (IOException e) {
            path = file.getAbsolutePath();
        } finally {
            return path;
        }
    }

    private Bitmap markAsSelected(Bitmap target) {
        Canvas canvas = new Canvas(target);
        canvas.drawBitmap(this.icon_image_selected, 0, 0, null);
        return target;
    }

    private ArrayList<GallerieItem> getData() {
        final ArrayList<GallerieItem> image_items = new ArrayList<>();
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        File file;
        String suffix;
        int file_width, file_height;

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
                    if(this.ifImageSuffix(suffix)) {
                        // get the width and height and try to open downsampled image
                        options.inSampleSize = 1;
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                        file_width = options.outWidth;
                        file_height = options.outHeight;
                        while(file_height / 2 > this.width_icon && file_width / 2 > this.width_icon) {
                            options.inSampleSize++;
                            file_height /= 2;
                            file_width /= 2;
                        }

                        options.inJustDecodeBounds = false;
                        bitmap = ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeFile(file.getAbsolutePath(), options),
                                this.width_icon, this.width_icon);
                        if(this.list_selected_items.contains(this.getFilePath(file))) {
                            bitmap = this.markAsSelected(bitmap);
                        }
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
            if(this.ifImageSuffix(suffix)) {
                // the selected item is an image
                // if in selection mode, then select or unselect
                // else, open editor
                if(this.if_select_mode) {
                    String path = this.getFilePath(file);
                    if(this.list_selected_items.contains(path)) {
                        // TODO fails to check the existence of selected items
                        BitmapFactory.Options options = new BitmapFactory.Options();

                        this.list_selected_items.remove(path);
                        options.inJustDecodeBounds = false;
                        item.setSymbol(ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeFile(path, options),
                                this.width_icon, this.width_icon));
                    }
                    else {
                        this.list_selected_items.add(path);
                        item.setSymbol(this.markAsSelected(item.getSymbol()));
                    }
                }
                else {
                    Intent in = new Intent(this, SwissKnife.class);
                    in.putExtra("path", file.getAbsolutePath());
                    startActivity(in);
                }
            }
            else if(suffix.compareTo(".func") == 0) {
                try {
                    Scanner reader = new Scanner(file);
                    String string_data = reader.nextLine();
                    this.alert(string_data);
                } catch (FileNotFoundException e) {
                    this.alert("File not found");
                }
            }
            else {
                this.alert("File not supported");
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
        this.button_set_sel_mode = (Button)findViewById(R.id.button_gallery_selectitem);
        this.button_back = (Button)findViewById(R.id.button_gallery_back);

        this.button_set_dir_home.setOnClickListener(this);
        this.button_set_dir_sdcard.setOnClickListener(this);
        this.button_set_dir_internal.setOnClickListener(this);
        this.button_set_sel_mode.setOnClickListener(this);
        this.button_back.setOnClickListener(this);

        this.if_select_mode = false;
        this.list_selected_items = new LinkedList<String>();

        this.textview_current_dir = (TextView)findViewById(R.id.textview_gallerie_current_dir);

        this.openDir(System.getenv("EXTERNAL_STORAGE"));
        this.width_icon = 100;
        this.icon_dir = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(getResources(), R.drawable.gallerie_dir_icon),
                this.width_icon, this.width_icon);
        this.icon_file = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(getResources(), R.drawable.gallerie_file_icon),
                this.width_icon, this.width_icon);
        this.icon_image_selected = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(getResources(), R.drawable.gallerie_icon_selected),
                this.width_icon, this.width_icon);

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
                if(this.if_select_mode) {
                    this.list_selected_items.clear();
                }
                else {
                    in = new Intent(this, main.class);
                    startActivity(in);
                }
                break;

            case R.id.button_gallery_selectitem:
                this.if_select_mode = !this.if_select_mode;
                if(this.if_select_mode) {
                    this.button_set_sel_mode.setText("Copy");
                    this.button_back.setText("Unsel");
                    this.list_selected_items.clear();
                }
                else {
                    this.button_set_sel_mode.setText("Sel");
                    this.button_back.setText("Back");

                    for(String str: this.list_selected_items) {
                        File src = new File(str);
                        String filename = src.getName();
                        File dst = new File(Environment.getExternalStorageDirectory()
                                + getString(R.string.workshop_dir) + "/" + filename);

                        try {
                            InputStream input_stream = new FileInputStream(src);
                            OutputStream output_stream = new FileOutputStream(dst);
                            long size = src.length();
                            byte[] bytes = new byte[(int)size];
                            input_stream.read(bytes);                            output_stream.write(bytes);
                            output_stream.close();
                            input_stream.close();
                        } catch(FileNotFoundException e) {
                            this.alert("Some files not found(Internal error)");
                        } catch(IOException e) {
                            this.alert("Writing failed(Internal error)");
                        }
                    }
                }
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
