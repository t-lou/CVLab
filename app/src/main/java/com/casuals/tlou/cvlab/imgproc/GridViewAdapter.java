package com.casuals.tlou.cvlab.imgproc;

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

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.casuals.tlou.cvlab.R;

import java.util.ArrayList;

public class GridViewAdapter extends ArrayAdapter {
    private static class ViewHolder {
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
