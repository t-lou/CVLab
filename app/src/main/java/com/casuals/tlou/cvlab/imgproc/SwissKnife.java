package com.casuals.tlou.cvlab.imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.gui.BasicDialog;
import com.casuals.tlou.cvlab.gui.GridViewItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
            ImageView holder;

            if (row == null) {
                LayoutInflater inflater = ((Activity) context).getLayoutInflater();
                row = inflater.inflate(this.resource, parent, false);
                holder = (ImageView)row.findViewById(R.id.swissknife_item_image);
                row.setTag(holder);
            } else {
                holder = (ImageView)row.getTag();
            }

            GridViewItem item = (GridViewItem)data.get(position);
            holder.setImageBitmap(item.getSymbol());
            return row;
        }
    }

    private ImageView imageview_canvas;
    private GridView gridview_tools;
    // private GridViewAdapter gridview_tools_adapter;

    private Bitmap image;
    // private Bitmap image_rendered;
    // private String name;
    private File file_origin;
    private File file_last_image;
    private boolean if_saved;
    private boolean if_need_display;

    private Filter filter;
    private JSONArray script_obj;
    private File script_file;

    private ArrayList<GridViewItem> getToolItems() {
        final ArrayList<GridViewItem> imageItems = new ArrayList<>();
        if(this.filter != null) {
            for (String str : this.filter.getFilterNames()) {
                int imageResource = getResources().getIdentifier("swissknife_tool_" + str, "drawable", getPackageName());
                Bitmap icon = BitmapFactory.decodeResource(getResources(), imageResource);
                imageItems.add(new GridViewItem(icon, str));
            }
        }

        return imageItems;
    }

    private void displayImage() {
        float ratio_width, ratio_height;
        int display_width, display_height;

        this.filter.copyCurrent(this.image);
        //this.image = this.filter.getCurrent().copy(this.filter.getCurrent().getConfig(), true);
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

    private void selectTool(int i) {
        // get name of tool
        GridViewItem item = (GridViewItem)gridview_tools.getItemAtPosition(i);
        final String name = item.getName();
        // possible parameters for tools
        final BasicDialog dialog_for_parameters = new BasicDialog(this);
        dialog_for_parameters.addDropdownSelector(new String[] {
                "Gray scale", "Red", "Green", "Blue", "Hue", "Saturation", "Value"});

        final JSONObject script_item;

        switch (name) {
            case "rgb_to_bw":
                this.filter.doRGB2BW();
                this.filter.waitTillEnd();
                this.displayImage();

                script_item = new JSONObject();
                try {
                    script_item.put("name", name);
                } catch(JSONException e) {
                    new BasicDialog(this, "Recording error");
                }
                finally {
                    this.script_obj.put(script_item);
                }
                break;
            case "rescale":
                dialog_for_parameters.setTitle("Parameter for rescaling");
                dialog_for_parameters.addTextEditor("Scaling factor, default 1.0");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        int id_channel = (Integer)dialog_for_parameters.getInputValue(0);
                        Float scaling_factor_obj = (Float)dialog_for_parameters.getInputValue(1);
                        float scaling_factor = 1.0f;

                        if (scaling_factor_obj != null) scaling_factor = scaling_factor_obj;

                        filter.doRescale(scaling_factor, id_channel);
                        filter.waitTillEnd();
                        displayImage();

                        JSONObject script_item_inner = new JSONObject();
                        try {
                            script_item_inner.put("name", name);
                            script_item_inner.put("channel", id_channel);
                            script_item_inner.put("scaling_factor", scaling_factor);
                        } catch (JSONException e) {
                            new BasicDialog(SwissKnife.this, "Recording error");
                        } finally {
                            script_obj.put(script_item_inner);
                        }
                    }
                });
                dialog_for_parameters.finish();
                break;
            case "gaussian":
                dialog_for_parameters.setTitle("Parameter for Gaussian filter");
                dialog_for_parameters.addTextEditor("Radius of filter, default 2");
                dialog_for_parameters.addTextEditor("Sigma, default 1.0");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                int radius = 2;
                                float sigma = 1.0f;
                                int id_channel = (Integer)dialog_for_parameters.getInputValue(0);
                                Float radius_obj = (Float)dialog_for_parameters.getInputValue(1);
                                Float sigma_obj = (Float)dialog_for_parameters.getInputValue(2);

                                if(radius_obj != null) radius = Math.round(radius_obj);
                                if(sigma_obj != null) sigma = sigma_obj;

                                filter.doGaussian(radius, id_channel, sigma);
                                filter.waitTillEnd();
                                displayImage();

                                JSONObject script_item_inner = new JSONObject();
                                try {
                                    script_item_inner.put("name", name);
                                    script_item_inner.put("channel", id_channel);
                                    script_item_inner.put("radius", radius);
                                    script_item_inner.put("sigma", sigma);
                                } catch (JSONException e) {
                                    new BasicDialog(SwissKnife.this, "Recording error");
                                } finally {
                                    script_obj.put(script_item_inner);
                                }
                            }
                        });
                dialog_for_parameters.finish();
                break;
            case "laplacian":
                dialog_for_parameters.setTitle("Parameter for Laplacian filter");
                dialog_for_parameters.addTextEditor("Scaling factor, default 1.0");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                int id_channel = (Integer) dialog_for_parameters.getInputValue(0);
                                Float scaling_factor_obj = (Float) dialog_for_parameters.getInputValue(1);
                                float scaling_factor = 1.0f;

                                if(scaling_factor_obj != null) scaling_factor = scaling_factor_obj;

                                filter.doLaplacian(id_channel, scaling_factor);
                                filter.waitTillEnd();
                                displayImage();

                                JSONObject script_item_inner = new JSONObject();
                                try {
                                    script_item_inner.put("name", name);
                                    script_item_inner.put("channel", id_channel);
                                    script_item_inner.put("scaling_factor", scaling_factor);
                                } catch (JSONException e) {
                                    new BasicDialog(SwissKnife.this, "Recording error");
                                } finally {
                                    script_obj.put(script_item_inner);
                        }
                    }
                });
                dialog_for_parameters.finish();
                break;
            case "gaussian_laplacian":
                dialog_for_parameters.setTitle("Parameter for Gaussian-Laplacian filter");
                dialog_for_parameters.addTextEditor("Radius, default 2");
                dialog_for_parameters.addTextEditor("Sigma, default 1.0");
                dialog_for_parameters.addTextEditor("Scaling factor, default 1.0");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        int radius = 2;
                        float sigma_gaussian = 1.0f, scaling_factor = 1.0f;
                        int id_channel = (Integer)dialog_for_parameters.getInputValue(0);
                        Float radius_obj = (Float)dialog_for_parameters.getInputValue(1);
                        Float sigma_gaussian_obj = (Float)dialog_for_parameters.getInputValue(2);
                        Float scaling_factor_obj = (Float)dialog_for_parameters.getInputValue(3);

                        if(radius_obj != null) radius = Math.round(radius_obj);
                        if(sigma_gaussian_obj != null) sigma_gaussian = sigma_gaussian_obj;
                        if(scaling_factor_obj != null) scaling_factor = scaling_factor_obj;

                        filter.doGaussianLaplacian(radius, id_channel, sigma_gaussian, scaling_factor);
                        filter.waitTillEnd();
                        displayImage();

                        JSONObject script_item_inner = new JSONObject();
                        try {
                            script_item_inner.put("name", name);
                            script_item_inner.put("channel", id_channel);
                            script_item_inner.put("radius", radius);
                            script_item_inner.put("sigma", sigma_gaussian);
                            script_item_inner.put("scaling_factor", scaling_factor);
                        } catch(JSONException e) {
                            new BasicDialog(SwissKnife.this, "Recording error");
                        }
                        finally {
                            script_obj.put(script_item_inner);
                        }
                    }
                });
                dialog_for_parameters.finish();
                break;
            case "bilateral":
                dialog_for_parameters.setTitle("Parameter for bilateral filter");
                dialog_for_parameters.addTextEditor("Radius, default 3");
                dialog_for_parameters.addTextEditor("Sigma for spatial filter, default 1.0");
                dialog_for_parameters.addTextEditor("Sigma for range filter, default 1.0");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        int radius = 2;
                        float sigma_spatial = 1.0f, sigma_range = 1.0f;
                        int id_channel = (Integer)dialog_for_parameters.getInputValue(0);
                        Float radius_obj = (Float)dialog_for_parameters.getInputValue(1);
                        Float sigma_spatial_obj = (Float)dialog_for_parameters.getInputValue(2);
                        Float sigma_range_obj = (Float)dialog_for_parameters.getInputValue(3);

                        if(radius_obj != null) radius = Math.round(radius_obj);
                        if(sigma_spatial_obj != null) sigma_spatial = sigma_spatial_obj;
                        if(sigma_range_obj != null) sigma_range = sigma_range_obj;

                        filter.doBilateral(radius, id_channel, sigma_spatial, sigma_range);
                        filter.waitTillEnd();
                        displayImage();

                        JSONObject script_item_inner = new JSONObject();
                        try {
                            script_item_inner.put("name", name);
                            script_item_inner.put("channel", id_channel);
                            script_item_inner.put("radius", radius);
                            script_item_inner.put("sigma_range", sigma_range);
                            script_item_inner.put("sigma_spatial", sigma_spatial);
                        } catch(JSONException e) {
                            new BasicDialog(SwissKnife.this, "Recording error");
                        }
                        finally {
                            script_obj.put(script_item_inner);
                        }
                    }
                });
                dialog_for_parameters.finish();
                break;
            case "mean":
                dialog_for_parameters.setTitle("Parameter for mean filter");
                dialog_for_parameters.addTextEditor("Radius, default 2");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        int radius = 2;
                        int id_channel = (Integer)dialog_for_parameters.getInputValue(0);
                        Float radius_obj = (Float)dialog_for_parameters.getInputValue(1);

                        if(radius_obj != null) radius = Math.round(radius_obj);

                        filter.doMean(radius, id_channel);
                        filter.waitTillEnd();
                        displayImage();

                        JSONObject script_item_inner = new JSONObject();
                        try {
                            script_item_inner.put("name", name);
                            script_item_inner.put("channel", id_channel);
                            script_item_inner.put("radius", radius);
                        } catch(JSONException e) {
                            new BasicDialog(SwissKnife.this, "Recording error");
                        }
                        finally {
                            script_obj.put(script_item_inner);
                        }
                    }
                });
                dialog_for_parameters.finish();
                break;
            case "threshold":
                dialog_for_parameters.setTitle("Parameter for thresholding");
                dialog_for_parameters.addTextEditor("Threshold, default 0.5");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        float threshold = 0.5f;
                        int id_channel = (Integer)dialog_for_parameters.getInputValue(0);
                        Float threshold_obj = (Float)dialog_for_parameters.getInputValue(1);

                        if(threshold_obj != null) threshold = threshold_obj;

                        filter.doThreshold(id_channel, threshold);
                        filter.waitTillEnd();
                        displayImage();

                        JSONObject script_item_inner = new JSONObject();
                        try {
                            script_item_inner.put("name", name);
                            script_item_inner.put("channel", id_channel);
                            script_item_inner.put("threshold", threshold);
                        } catch(JSONException e) {
                            new BasicDialog(SwissKnife.this, "Recording error");
                        }
                        finally {
                            script_obj.put(script_item_inner);
                        }
                    }
                });
                dialog_for_parameters.finish();
                break;
            case "sobol":
                dialog_for_parameters.setTitle("Parameter for sobol filter");
                dialog_for_parameters.addDropdownSelector(new String[]{"both", "x", "y"});

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                int id_channel = (Integer) dialog_for_parameters.getInputValue(0);
                                int direction = (Integer) dialog_for_parameters.getInputValue(1);

                                filter.doSobol(id_channel, direction);
                                filter.waitTillEnd();
                                displayImage();

                                JSONObject script_item_inner = new JSONObject();
                                try {
                                    script_item_inner.put("name", name);
                                    script_item_inner.put("channel", id_channel);
                                    script_item_inner.put("direction", direction);
                                } catch (JSONException e) {
                                    new BasicDialog(SwissKnife.this, "Recording error");
                                } finally {
                                    script_obj.put(script_item_inner);
                                }
                            }
                        });
                dialog_for_parameters.finish();
                break;
            case "feature_detector_harris":
                dialog_for_parameters.setTitle("Parameter for Harris detector");
                dialog_for_parameters.addTextEditor("Alpha, usually 0.04-0.06, default 0.05");

                dialog_for_parameters.getDialogBuilder().setPositiveButton("Do it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                float alpha = 0.05f;
                                Float alpha_obj = (Float) dialog_for_parameters.getInputValue(1);

                                if (alpha_obj != null) alpha = alpha_obj;

                                filter.doHarrisDetect(alpha);
                                filter.waitTillEnd();
                                displayImage();

                                JSONObject script_item_inner = new JSONObject();
                                try {
                                    script_item_inner.put("name", name);
                                    script_item_inner.put("alpha", alpha);
                                } catch (JSONException e) {
                                    new BasicDialog(SwissKnife.this, "Recording error");
                                } finally {
                                    script_obj.put(script_item_inner);
                                }
                            }
                        });
                dialog_for_parameters.finish();
                break;
            default:
                break;
        }
        this.if_saved = false;
        this.saveFilterScript(this.script_file.getAbsolutePath());
    }

    private void saveFilterScript(String filename) {
        try {
            File filter_file = new File(filename);
            if(filter_file.exists()) {
                filter_file.delete();
            }
            FileOutputStream output_stream = new FileOutputStream(filter_file);
            OutputStreamWriter writer = new OutputStreamWriter(output_stream);
            writer.write(this.script_obj.toString());
            writer.close();
            output_stream.close();
        } catch(FileNotFoundException e) {
            new BasicDialog(this, "File cannot write");
        } catch (IOException e) {
            new BasicDialog(this, "Writing error");
        }
    }

    private void saveImage(Bitmap image, File file) {
        FileOutputStream output = null;
        try {
            if(file.exists()) {
                file.delete();
            }
            output = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception e) {
            new BasicDialog(this, "Image file not created");
        } finally {
            try {
                output.close();
            } catch (IOException e) {
                new BasicDialog(this, "Saving image failed(IOException)");
            } catch(NullPointerException e) {
                new BasicDialog(this, "Saving image failed(NullPointerException)");
            }
        }
    }

    private void openImage(File file) {
        int file_width, file_height;
        final File file_copy = file;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        file_width = options.outWidth;
        file_height = options.outHeight;

        if(file_width > 2048 || file_height > 2048) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Image is large");
            builder.setMessage("Image is quite huge, may be slow or even cause memory problem, " +
                    "load smaller version? \nIf the application returns to main menu, then " +
                    "probably there is not enough memory for image.");
            builder.setNegativeButton("No thanks", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    image = null;
                    filter.resetData();
                    image = BitmapFactory.decodeFile(file_copy.getAbsolutePath());
                    filter.setData(image);
                    new BasicDialog(SwissKnife.this, "Original image opened");
                }
            });
            builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    new BasicDialog(SwissKnife.this, "Schinked image opened");
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();

            while(file_width> 1024 && file_height > 1024) {
                file_width /= 2;
                file_height /= 2;
                options.inSampleSize++;
            }
        }
        options.inJustDecodeBounds = false;
        this.image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swissknife);

        this.imageview_canvas = (ImageView)findViewById(R.id.swissknife_imageview_canvas);

        Button button_back = (Button)findViewById(R.id.button_swissknife_back);
        Button button_save = (Button)findViewById(R.id.button_swissknife_save);
        Button button_undo = (Button)findViewById(R.id.button_swissknife_undo);

        button_back.setOnClickListener(this);
        button_save.setOnClickListener(this);
        button_undo.setOnClickListener(this);

        this.filter = new Filter(this);
        this.filter.resetData();

        this.script_obj = new JSONArray();
        this.script_file = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.script_dir) + "/unnamed.func");

        this.gridview_tools = (GridView)findViewById(R.id.gridview_swissknife_tools);
        GridViewAdapter gridview_tools_adapter = new GridViewAdapter(this, R.layout.swissknife_tool_item,
                this.getToolItems());
        this.gridview_tools.setAdapter(gridview_tools_adapter);

        this.gridview_tools.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> gridview, View v, int i, long l) {
                        selectTool(i);
                    }
                }
        );

        String name = getIntent().getExtras().getString("path");
        if(name != null) {
            this.file_origin = new File(name);
        }
        else {
            this.file_origin = null;
            new BasicDialog(this, "No file passed");
        }
        // open scaled image
        this.openImage(this.file_origin);
        // this.image_rendered = null;

        this.filter.setData(this.image);

        this.if_saved = true;
        this.if_need_display = true;

        this.file_last_image = Environment.getExternalStoragePublicDirectory(
                getString(R.string.cache_dir) + "/last.png");
        this.saveImage(this.image, this.file_last_image);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if(this.if_need_display) {
            this.displayImage();
            this.if_need_display = false;
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.button_swissknife_undo:
                this.filter.resetData();
                this.image = BitmapFactory.decodeFile(this.file_last_image.getAbsolutePath());
                this.filter.setData(this.image);
                this.displayImage();

                if(this.script_file.exists()) {
                    this.script_file.delete();
                }
                this.script_obj = new JSONArray();
                break;

            case R.id.button_swissknife_save:
                final BasicDialog dialog_filename = new BasicDialog(this);
                dialog_filename.setTitle("Will savev image and/or filter");
                dialog_filename.addTextEditor("File name for image");
                dialog_filename.addTextEditor("File name for filter");

                dialog_filename.getDialogBuilder().setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String img_name = ((EditText)dialog_filename.getContentDirectly(0)).getText().toString();
                        String script_name = ((EditText)dialog_filename.getContentDirectly(1)).getText().toString();
                        if(img_name.length() > 0) {
                            File image_file = new File(Environment.getExternalStorageDirectory()
                                    + getString(R.string.img_dir) + "/"
                                    + img_name + ".png");
                            saveImage(image, image_file);

                            file_origin = new File(image_file.getAbsolutePath());
                            file_last_image = new File(image_file.getAbsolutePath());
                        }
                        if(script_name.length() > 0) {
                            File filter_file = new File(Environment.getExternalStorageDirectory()
                                    + getString(R.string.script_dir) + "/"
                                    + script_name +".func");
                            saveFilterScript(filter_file.getAbsolutePath());
                        }
                    }
                });
                dialog_filename.finish();

                this.if_saved = true;
                break;

            case R.id.button_swissknife_back:
                if(this.if_saved) {
                    Intent intent = new Intent(SwissKnife.this, Gallerie.class);
                    startActivity(intent);
                }
                else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Return to gallery");
                    builder.setMessage("Image not save, are you sure?");
                    builder.setNegativeButton("No wait", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
                    builder.setPositiveButton("Absolutely", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(SwissKnife.this, Gallerie.class);
                            startActivity(intent);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                break;
        }
    }
}
