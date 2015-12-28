package com.casuals.tlou.cvlab.imgproc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.main;

import java.io.File;
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
    private boolean if_saved;
    private String name;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swissknife);

        this.imageview_canvas = (ImageView)findViewById(R.id.swissknife_imageview_canvas);

        this.button_back = (Button)findViewById(R.id.button_swissknife_back);
        this.button_save = (Button)findViewById(R.id.button_swissknife_save);
        this.button_undo = (Button)findViewById(R.id.button_swissknife_undo);

        this.button_back.setOnClickListener(this);
        this.button_save.setOnClickListener(this);
        this.button_undo.setOnClickListener(this);

        this.filter = new Filter();
        // TODO gridview from list of tools
        this.gridview_tools = (GridView)findViewById(R.id.gridview_swissknife_tools);
        this.gridview_tools_adapter = new GridViewAdapter(this, R.layout.swissknife_tool_item,
                this.getToolItems());
        this.gridview_tools.setAdapter(this.gridview_tools_adapter);

        this.name = getIntent().getExtras().getString("path");
        this.image = BitmapFactory.decodeFile(new File(this.name).getAbsolutePath());
        this.image_rendered = this.image.copy(this.image.getConfig(), true);
        this.imageview_canvas.setImageBitmap(this.image_rendered);

        this.if_saved = true;
    }

    @Override
    public void onClick(View v) {
        Intent in;
        switch(v.getId()) {
            case R.id.button_swissknife_undo:
                break;

            case R.id.button_swissknife_save:
                break;

            case R.id.button_swissknife_back:
                in = new Intent(this, main.class);
                startActivity(in);
                break;
        }
    }
}
