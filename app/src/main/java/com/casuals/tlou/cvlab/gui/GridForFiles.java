package com.casuals.tlou.cvlab.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.ThumbnailUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.casuals.tlou.cvlab.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * Created by tlou on 29.02.16.
 */
public class GridForFiles {
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
            ViewHolder holder;

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

            GridViewItem item = (GridViewItem)data.get(position);
            holder.text.setText(item.getName());
            holder.image.setImageBitmap(item.getSymbol());
            return row;
        }
    }

    private Context context;
    private GridView gridview_items;

    private String current_dir;
    private String[] entries;
    private Bitmap icon_file;
    private Bitmap icon_dir;
    private Bitmap icon_image_selected;
    private int width_icon;

    private boolean if_select_mode;
    private LinkedList<String> list_selected_items;

    public GridForFiles(Context context, GridView gridview) {
        this.context = context;

        this.if_select_mode = false;
        this.list_selected_items = new LinkedList<>();

        this.openDir(System.getenv("EXTERNAL_STORAGE"));
        this.width_icon = 100;
        this.icon_dir = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(this.context.getResources(), R.drawable.gallerie_dir_icon),
                this.width_icon, this.width_icon);
        this.icon_file = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(this.context.getResources(), R.drawable.gallerie_file_icon),
                this.width_icon, this.width_icon);
        this.icon_image_selected = ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeResource(this.context.getResources(), R.drawable.gallerie_icon_selected),
                this.width_icon, this.width_icon);

        this.gridview_items = gridview;
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

    private boolean ifImageSuffix(String suffix) {
        return suffix.compareTo(".png") == 0 || suffix.compareTo(".jpeg") == 0
                || suffix.compareTo(".jpg") == 0 || suffix.compareTo(".dng") == 0
                || suffix.compareTo(".tiff") == 0;
    }

    private String getFilePath(File file) {
        String path;
        try {
            path = file.getCanonicalPath();
        } catch (IOException e) {
            path = file.getAbsolutePath();
        }
        return path;
    }

    private Bitmap markAsSelected(Bitmap target) {
        Canvas canvas = new Canvas(target);
        canvas.drawBitmap(this.icon_image_selected, 0, 0, null);
        return target;
    }

    private ArrayList<GridViewItem> getData() {
        final ArrayList<GridViewItem> image_items = new ArrayList<>();
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        File file;
        String suffix;
        int file_width, file_height;

        if(this.current_dir.length() > 0 && this.entries != null) {
            bitmap = this.icon_dir.copy(conf, true);
            image_items.add(new GridViewItem(bitmap, ".."));

            for (String str : this.entries) {
                file = new File(this.current_dir + "/" + str);
                if(!file.exists()) {
                    continue;
                }

                if(file.isDirectory()) {
                    bitmap = this.icon_dir.copy(conf, true);
                    if(this.list_selected_items.contains(this.getFilePath(file))) {
                        bitmap = this.markAsSelected(bitmap);
                    }
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
                image_items.add(new GridViewItem(bitmap, str));
            }
        }
        return image_items;
    }

    public boolean openDir(String name) {
        File file = new File(name);
        boolean result;
        result = file.exists() && file.isDirectory();

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
        return result;
    }

    private void selectItem(int i) {
        GridViewItem item = (GridViewItem)this.gridview_items.getItemAtPosition(i);
        String name = item.getName();
        File file = new File(this.current_dir + "/" + name);
        if(file.isDirectory()) {
            // the selected item is a directory
            // if in selection mode, then select or unselect all in dir
            // else, open dir
            if(!item.getName().equals("..") && this.if_select_mode) {
                String path = this.getFilePath(file);
                String[] children = file.list();
                if(this.list_selected_items.contains(path)) {
                    for(String child : children) {
                        child = path + "/" + child;
                        if(this.list_selected_items.contains(child)) {
                            this.list_selected_items.remove(child);
                        }
                    }
                    this.list_selected_items.remove(path);
                    item.setSymbol(this.icon_dir);
                }
                else {
                    for(String child : children) {
                        child = path + "/" + child;
                        if(!this.list_selected_items.contains(child)) {
                            this.list_selected_items.add(child);
                        }
                    }
                    this.list_selected_items.add(path);
                    item.setSymbol(this.markAsSelected(item.getSymbol()));
                }
                this.gridview_items.invalidateViews();
            }
            else {
                this.onClickDir(this.current_dir + "/" + name);
            }
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
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = false;
                        this.list_selected_items.remove(path);
                        item.setSymbol(ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeFile(path, options),
                                this.width_icon, this.width_icon));
                    }
                    else {
                        this.list_selected_items.add(path);
                        item.setSymbol(this.markAsSelected(item.getSymbol()));
                    }
                    this.gridview_items.invalidateViews();
                }
                else {
                    this.onClickImage(file.getAbsolutePath());
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

    public void updateView() {
        GridViewAdapter gridview_items_adapter =
                new GridViewAdapter(this.context, R.layout.gallerie_item, this.getData());
        this.gridview_items.setAdapter(gridview_items_adapter);
    }

    public void alert(String message) {
        AlertDialog.Builder alert;

        alert = new AlertDialog.Builder(this.context);
        alert.setMessage(message)
                .setCancelable(true);
        alert.show();
    }

    public LinkedList<String> getSelectedItems() { return this.list_selected_items; }

    public String getCurrentDir() { return this.current_dir; }

    public boolean ifSelectMode() { return this.if_select_mode; }
    public void invSelectMode() { this.if_select_mode = !this.if_select_mode; }

    public void onClickImage(String path) {}

    public void onClickDir(String path) {
        this.current_dir = path;
        this.openDir(this.current_dir);
        this.updateView();
    }
}
