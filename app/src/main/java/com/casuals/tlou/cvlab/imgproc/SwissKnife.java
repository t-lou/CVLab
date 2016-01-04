package com.casuals.tlou.cvlab.imgproc;

import android.app.Activity;
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

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.main;

import java.io.File;
import java.io.FileOutputStream;
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

public class SwissKnife extends Activity implements View.OnClickListener {

    private class GridViewAdapter extends ArrayAdapter {
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
            ImageView holder = null;

            if (row == null) {
                LayoutInflater inflater = ((Activity) context).getLayoutInflater();
                row = inflater.inflate(this.resource, parent, false);
                holder = (ImageView)row.findViewById(R.id.swissknife_item_image);
                row.setTag(holder);
            } else {
                holder = (ImageView)row.getTag();
            }

            GallerieItem item = (GallerieItem)data.get(position);
            holder.setImageBitmap(item.getSymbol());
            return row;
        }
    }

    private ImageView imageview_canvas;
    private GridView gridview_tools;
    private GridViewAdapter gridview_tools_adapter;
    private Button button_back;
    private Button button_undo;
    private Button button_save;

    private Bitmap image;
    private Bitmap image_rendered;
    private Bitmap image_last;
    private String name;
    private boolean if_saved;
    private boolean if_colorful;

    private Filter filter;

    private ArrayList<GallerieItem> getToolItems() {
        final ArrayList<GallerieItem> imageItems = new ArrayList<>();
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        if(this.filter != null) {
            for (String str : this.filter.getFilterNames()) {
                int imageResource = getResources().getIdentifier("swissknife_tool_" + str, "drawable", getPackageName());
                Bitmap icon = BitmapFactory.decodeResource(getResources(), imageResource);
                imageItems.add(new GallerieItem(icon, str));
            }
        }

        return imageItems;
    }

    private void selectTool(int i) {
        // get name of tool
        GallerieItem item = (GallerieItem)gridview_tools.getItemAtPosition(i);
        String name = item.getName();
        // for display
        float ratio_width, ratio_height;
        int display_width, display_height;
        // possible parameters for tools
        int radius = 20, id_channel_colorful = -1;
        float sigma_gaussian = 1.0f;

        // reset the image
        this.image_last = this.image.copy(this.image.getConfig(), true);
        this.image = null;
        this.image_rendered = null;
        this.imageview_canvas.setImageBitmap(null);
        this.debug.setText(name);
        switch (name) {
            case "rgb_to_bw":
                this.filter.doRGB2BW();
                this.if_colorful = false;
                break;
            case "gaussian":
                // if colorful then choose channel, now do to all
                this.filter.doGaussian(radius, id_channel_colorful, sigma_gaussian);
                break;
            case "mean":
                // if colorful then choose channel, now do to all
                this.filter.doMean(radius, id_channel_colorful);
                break;
            default:
                this.debug.setText("ERROR");
        }
        this.debug.append(" finished");

        this.if_saved = false;

        this.image = this.filter.getCurrent().copy(this.filter.getCurrent().getConfig(), true);
        // here display the whole image, modify if part of image would be focused(zoom in)
        // then the target would be this.image_rendered
        ratio_width = (float)this.imageview_canvas.getWidth() / (float)this.image.getWidth();
        ratio_height = (float)this.imageview_canvas.getHeight() / (float)this.image.getHeight();
        if(ratio_width < ratio_height) {
            display_width = this.imageview_canvas.getWidth();
            display_height = this.image.getHeight() * this.imageview_canvas.getWidth()
                    / this.image.getWidth();
        }
        else {
            display_height = this.imageview_canvas.getHeight();
            display_width = this.image.getWidth() * this.imageview_canvas.getHeight()
                    / this.image.getHeight();
        }
        this.imageview_canvas.setImageBitmap(
                ThumbnailUtils.extractThumbnail(this.image, display_width, display_height));
    }

    private TextView debug;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swissknife);

        this.debug = (TextView)findViewById(R.id.textView);

        this.imageview_canvas = (ImageView)findViewById(R.id.swissknife_imageview_canvas);

        this.button_back = (Button)findViewById(R.id.button_swissknife_back);
        this.button_save = (Button)findViewById(R.id.button_swissknife_save);
        this.button_undo = (Button)findViewById(R.id.button_swissknife_undo);

        this.button_back.setOnClickListener(this);
        this.button_save.setOnClickListener(this);
        this.button_undo.setOnClickListener(this);

        this.filter = new Filter(this);

        this.gridview_tools = (GridView)findViewById(R.id.gridview_swissknife_tools);
        this.gridview_tools_adapter = new GridViewAdapter(this, R.layout.swissknife_tool_item,
                this.getToolItems());
        this.gridview_tools.setAdapter(this.gridview_tools_adapter);

        this.gridview_tools.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> gridview, View v, int i, long l) {
                        selectTool(i);
                    }
                }
        );

        this.name = getIntent().getExtras().getString("path");
        this.image = BitmapFactory.decodeFile(new File(this.name).getAbsolutePath());
        this.image_rendered = this.image.copy(this.image.getConfig(), true);
        this.image_last = null;
        this.imageview_canvas.setImageBitmap(this.image_rendered);

        this.filter.setData(this.image);

        this.if_saved = true;
        this.if_colorful = true;
    }

    @Override
    public void onClick(View v) {
        Intent in;
        switch(v.getId()) {
            case R.id.button_swissknife_undo:
                this.image = this.image_last.copy(this.image_last.getConfig(), true);
                this.image_last = null;
                this.imageview_canvas.setImageBitmap(this.image);
                this.filter.setData(this.image);
                break;

            case R.id.button_swissknife_save:
                if(this.image_last != null && this.filter != null) {
                    FileOutputStream output = null;
                    this.debug.setText(this.name);
                    try {
                        File file = new File(this.name);
                        file.delete();
                        output = new FileOutputStream(this.name);
                        this.filter.getCurrent().compress(Bitmap.CompressFormat.PNG, 100, output);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                this.if_saved = true;
                break;

            case R.id.button_swissknife_back:
                in = new Intent(this, main.class);
                startActivity(in);
                break;
        }
    }
}
