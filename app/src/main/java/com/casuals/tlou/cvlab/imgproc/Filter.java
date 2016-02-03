package com.casuals.tlou.cvlab.imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

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
public class Filter {
    private class FilterItem {
        public String filter_name;
        public int[] int_params;
        public float[] float_params;

        public FilterItem(String name, int num_int, int num_float) {
            this.int_params = new int[num_int];
            this.float_params = new float[num_float];
            this.filter_name = name;
        }
    }

    private String[] available_filters = {"rgb_to_bw", "rescale", "up_pyramid",
            "gaussian", "laplacian", "gaussian_laplacian", "mean", "bilateral",
            "threshold"};

    private RenderScript render_script;
    private ScriptC_imgproc script;

    private Allocation allocation_in;
    private Allocation allocation_out;
    private Allocation allocation_context;
    private Allocation allocation_input_yuv;
    private Allocation allocation_output_rgba;

    private ScriptIntrinsicYuvToRGB yuv_rgba_convertor;

    private int id_channel;
    private int height, width;

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock lock_batch = new ReentrantLock();

    private FilterItem[] batch_items;

    public Filter(Context context) {
        this.render_script = RenderScript.create(context);
        this.script = new ScriptC_imgproc(render_script);
        this.batch_items = null;

        this.allocation_output_rgba = null;
        this.allocation_input_yuv = null;
        this.allocation_out = null;
        this.allocation_in = null;
        this.allocation_context = null;
    }

    public void setData(Bitmap source) {
        Bitmap image0 = source.copy(source.getConfig(), true);
        Bitmap image1 = source.copy(source.getConfig(), true);
        Bitmap image2 = source.copy(source.getConfig(), true);

        this.allocation_in = Allocation.createFromBitmap(this.render_script, image0);
        this.allocation_out = Allocation.createFromBitmap(this.render_script, image1);
        this.allocation_context = Allocation.createFromBitmap(this.render_script, image2);

        this.height = source.getHeight();
        this.width = source.getWidth();

        this.id_channel = -1;
    }

    public void setDataFromYUV(byte[] data) {
        this.lock.lock();
        this.allocation_input_yuv.copyFrom(data);
        this.yuv_rgba_convertor.setInput(this.allocation_input_yuv);
        this.yuv_rgba_convertor.forEach(this.allocation_output_rgba);
        this.script.set_context(this.allocation_output_rgba);
        this.script.forEach_transpose(this.allocation_out, this.allocation_in);

        this.id_channel = -1;
        this.lock.unlock();
    }

    public void prepareForYUV(Size size) {
        this.width = size.getHeight();
        this.height = size.getWidth();

        this.yuv_rgba_convertor = ScriptIntrinsicYuvToRGB.create(this.render_script,
                Element.RGBA_8888(this.render_script));

        Type.Builder type_yuv = new Type.Builder(this.render_script, Element.U8(this.render_script))
                .setX(this.height).setY(this.width).setYuvFormat(ImageFormat.NV21);
        this.allocation_input_yuv = Allocation.createTyped(this.render_script,
                type_yuv.create(), Allocation.USAGE_SCRIPT);

        Type.Builder type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.height).setY(this.width);
        this.allocation_output_rgba = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);

        type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.width).setY(this.height);
        this.allocation_in = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
        this.allocation_out = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
        this.allocation_context = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
    }

    public void setDataFromBitmap(Bitmap image) {
        this.lock.lock();
        this.allocation_output_rgba.copyFrom(image);
        this.script.set_context(this.allocation_output_rgba);
        this.script.forEach_transpose(this.allocation_in, this.allocation_out);
        this.script.set_context(this.allocation_out);
        this.script.set_width(this.width);
        this.script.forEach_flip_vertical(this.allocation_out, this.allocation_in);

        this.id_channel = -1;
        this.lock.unlock();
    }

    public void prepareForBitmap(Size size) {
        this.width = size.getHeight();
        this.height = size.getWidth();

        Type.Builder type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.width).setY(this.height);
        this.allocation_in = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
        this.allocation_out = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
        this.allocation_context = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);

        type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.height).setY(this.width);
        this.allocation_output_rgba = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
    }

    public boolean ifNotReadyForVideoInput() {
        return allocation_output_rgba == null;
    }

    public void resetData() {
        this.allocation_in = null;
        this.allocation_out = null;
        this.allocation_context = null;
        this.batch_items = null;
    }

    public void preprecess(int index) {
        if(index < 0 || index > 6) index = 0;
        // init
        if(this.id_channel < 0) {
            this.script.set_index_channel(index);
            this.allocation_context.copyFrom(this.allocation_in);
            this.script.forEach_encode(this.allocation_context, this.allocation_in);
        }
        // switch channel, this.allocation_context should be the original image
        else if(this.id_channel != index) {
            this.script.set_context(this.allocation_context);
            this.script.set_index_channel(this.id_channel);
            this.script.forEach_decode_with_context(this.allocation_in, this.allocation_out);
            this.script.set_index_channel(index);
            this.script.forEach_encode(this.allocation_out, this.allocation_in);
        }

        this.id_channel = index;
    }

    public void doRGB2BW() {
        this.lock.lock();

        this.script.forEach_rgb_to_bw(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);
        this.script.forEach_copy(this.allocation_out, this.allocation_context);

        this.lock.unlock();
    }

    public void doRescale(float scale, int idChannel) {
        this.lock.lock();

        this.preprecess(idChannel);

        this.script.set_scale(scale);
        this.script.forEach_rescale(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doUpPyramid() {
        this.lock.lock();

        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap image0 = Bitmap.createBitmap(this.width / 2, this.height / 2, conf);
        Bitmap image1 = Bitmap.createBitmap(this.width / 2, this.height / 2, conf);
        Bitmap image2 = Bitmap.createBitmap(this.width / 2, this.height / 2, conf);

        this.script.set_index_channel(this.id_channel);
        this.script.set_context(this.allocation_in);
        this.allocation_in = Allocation.createFromBitmap(this.render_script, image0);
        this.allocation_out = Allocation.createFromBitmap(this.render_script, image1);
        this.script.forEach_up_pyramid(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.width /= 2;
        this.height /= 2;

        this.allocation_context = Allocation.createFromBitmap(this.render_script, image2);

        this.lock.unlock();
    }

    public void doGaussian(int radius, int idChannel, float sigma) {
        this.lock.lock();

        float[] mask = this.createGaussianMask(radius, sigma);
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprecess(idChannel);

        this.script.set_scale(1.0f);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.height);
        this.script.set_width(this.width);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doLaplacian(int idChannel, float scale) {
        this.lock.lock();

        float[] mask = this.createLaplacianMask();
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprecess(idChannel);

        this.script.set_scale(scale);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.height);
        this.script.set_width(this.width);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(1);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doGaussianLaplacian(int radius, int idChannel, float sigma, float scale) {
        this.lock.lock();

        float[] mask = this.createGaussianLaplacianMask(radius, sigma);
        for(int i = 0; i < mask.length; i++) {
            mask[i] *= scale;
        }
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprecess(idChannel);

        this.script.set_scale(1.0f);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.height);
        this.script.set_width(this.width);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doMean(int radius, int idChannel) {
        this.lock.lock();

        this.preprecess(idChannel);

        this.script.set_context(this.allocation_in);
        this.script.set_height(this.height);
        this.script.set_width(this.width);
        this.script.set_radius(radius);
        this.script.forEach_mean(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doBilateral(int radius, int idChannel, float sigma_spatial, float sigma_range) {
        this.lock.lock();

        float[] mask = this.createGaussianMask(radius, sigma_spatial);
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprecess(idChannel);

        this.script.set_scale(1.0f);
        this.script.set_threshold_value(2.0f * sigma_range * sigma_range);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.height);
        this.script.set_width(this.width);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_bilateral(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doThreshold(int idChannel, float thresholdValue) {
        this.lock.lock();

        this.preprecess(idChannel);

        this.script.set_threshold_value(thresholdValue);
        this.script.set_index_channel(idChannel);
        this.script.forEach_threshold(this.allocation_in, this.allocation_out);
        this.script.forEach_copy(this.allocation_out, this.allocation_in);

        this.id_channel = 0;

        this.lock.unlock();
    }

    public void copyCurrentWithContext(Bitmap image) {
        if(this.id_channel >= 0) {
            this.allocation_context = Allocation.createFromBitmap(this.render_script, image);
            this.script.set_index_channel(this.id_channel);
            this.script.set_context(this.allocation_context);
            this.script.forEach_decode_with_context(this.allocation_in, this.allocation_out);
            this.allocation_out.copyTo(image);
        }
        else {
            this.allocation_in.copyTo(image);
        }
    }

    public void copyCurrent(Bitmap image) {
        if(this.id_channel >= 0) {
            this.script.set_index_channel(this.id_channel);
            this.script.forEach_decode(this.allocation_in, this.allocation_out);
            this.allocation_out.copyTo(image);
        }
        else {
            this.allocation_in.copyTo(image);
        }
    }

    public String[] getFilterNames () {
        return this.available_filters;
    }

    private float[] createGaussianMask(int radius, float sigma) {
        int length = 2 * radius + 1, index = 0;
        float[] mask = new float[length * length];
        float sum = 0.0f;

        for(int i = -radius; i <= radius; i++) {
            for(int j = -radius; j <= radius; j++) {
                mask[index] = (float)Math.exp(-(double)((float) (i * i) + (float) (j * j)) / (2.0f * sigma * sigma));
                sum += mask[index];
                index++;
            }
        }

        for(int i = 0; i < length * length; i++) {
            mask[i] /= sum;
        }

        return mask;
    }

    private float[] createLaplacianMask() {
        float[] mask = {-0.5f, -1.0f, -0.5f, -1.0f, 6.0f, -1.0f, -0.5f, -1.0f, -0.5f};
        return mask;
    }

    private float[] createGaussianLaplacianMask(int radius, float sigma) {
        int length = 2 * radius + 1;
        float[] mask_gaussian = this.createGaussianMask(radius, sigma);
        float[] mask_laplacian = this.createLaplacianMask();
        float[] mask = new float[length * length];
        int index = 0;
        float sum = 0.0f;

        for(int i = -radius; i <= radius; i++) {
            for(int j = -radius; j <= radius; j++) {
                int inner_index = 0;
                mask[index] = 0.0f;
                for(int ii = -1; ii <= 1; ii++) {
                    for(int jj = -1; jj <= 1; jj++) {
                        if(i + ii >= -radius && i + ii <= radius
                                && j + jj >= -radius && j + jj <= radius) {
                            mask[index] += mask_gaussian[index + jj + ii * length]
                                    * mask_laplacian[inner_index];
                        }
                        inner_index++;
                    }
                }
                sum += mask[index];
                index++;
            }
        }

        for(int i = 0; i < index; i++) {
            mask[i] /= sum;
        }

        return mask;
    }

    public void waitTillEnd() {
        this.lock.lock();
        this.lock.unlock();
    }

    public int loadBatch(File file) {
        int result = 0;
        try {
            Scanner reader = new Scanner(file);
            String string_data = reader.nextLine();
            JSONArray json_parser = new JSONArray(string_data);

            int num_filter = json_parser.length();
            this.batch_items = new FilterItem[num_filter];
            for(int i = 0; i < num_filter; i++) {
                String name;
                JSONObject json_obj = json_parser.getJSONObject(i);
                name = json_obj.optString("name");
                switch (name) {
                    case "rgb_to_bw":
                    case "up_pyramid":
                        this.batch_items[i] = new FilterItem(name, 0, 0);
                        break;
                    case "rescale":
                        this.batch_items[i] = new FilterItem(name, 1, 1);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("scaling_factor");
                        break;
                    case "gaussian":
                        this.batch_items[i] = new FilterItem(name, 2, 1);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].int_params[1] = json_obj.getInt("radius");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("sigma");
                        break;
                    case "laplacian":
                        this.batch_items[i] = new FilterItem(name, 1, 1);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("scaling_factor");
                        break;
                    case "gaussian_laplacian":
                        this.batch_items[i] = new FilterItem(name, 2, 2);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].int_params[1] = json_obj.getInt("radius");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("sigma");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("scaling_factor");
                        break;
                    case "mean":
                        this.batch_items[i] = new FilterItem(name, 2, 0);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].int_params[1] = json_obj.getInt("radius");
                    case "bilateral":
                        this.batch_items[i] = new FilterItem(name, 2, 2);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].int_params[1] = json_obj.getInt("radius");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("sigma_range");
                        this.batch_items[i].float_params[1] = (float)json_obj.getDouble("sigma_spatial");
                        break;
                    case "threshold":
                        this.batch_items[i] = new FilterItem(name, 1, 1);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("threshold");
                        break;
                    default:
                        this.batch_items[i] = new FilterItem("idle", 1, 1);
                        break;
                }
            }
        } catch (FileNotFoundException e) {
            result = 100;
        } catch (JSONException e) {
            result = 200;
        }
        return result;
    }

    public void doBatch() {
        this.lock_batch.lock();

        if(this.batch_items != null) {
            for(FilterItem item : this.batch_items) {
                switch (item.filter_name) {
                    case "rgb_to_bw":
                    case "up_pyramid":
                        this.doRGB2BW();
                        break;
                    case "rescale":
                        this.doRescale(item.float_params[0], item.int_params[0]);
                        break;
                    case "gaussian":
                        this.doGaussian(item.int_params[1], item.int_params[0], item.float_params[0]);
                        break;
                    case "laplacian":
                        this.doLaplacian(item.int_params[0], item.float_params[0]);
                        break;
                    case "gaussian_laplacian":
                        this.doGaussianLaplacian(item.int_params[1], item.int_params[0],
                                item.float_params[0], item.float_params[1]);
                        break;
                    case "mean":
                        this.doMean(item.int_params[1], item.int_params[0]);
                        break;
                    case "bilateral":
                        this.doBilateral(item.int_params[1], item.int_params[0],
                                item.float_params[1], item.float_params[0]);
                        break;
                    case "threshold":
                        this.doThreshold(item.int_params[0], item.float_params[0]);
                        break;
                    default:
                        break;
                }
            }
        }

        this.lock_batch.unlock();
    }

    public void resetBatch() {
        this.batch_items = null;
    }

    public void waitTillBatchEnd() {
        this.lock_batch.lock();
        this.lock_batch.unlock();
    }
}
