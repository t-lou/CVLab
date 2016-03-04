package com.casuals.tlou.cvlab.imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Size;

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

    private String[] available_filters = {"rgb_to_bw", "rescale", "gaussian",
            "laplacian", "gaussian_laplacian", "mean", "bilateral", "threshold",
            "sobol", "feature_detector_harris"};
    //TODO UpPyramid may be useless, consider deleting the icon later

    private RenderScript render_script;
    private ScriptC_imgproc script;

    private int index_allocation;
    private int num_allocation;
    private Allocation[] allocations;
    private Allocation allocation_context;
    private Allocation allocation_input_yuv;
    private Allocation allocation_output_rgba;

    private int id_channel;
    private int height, width;

    private ReentrantLock lock = new ReentrantLock();
    private ReentrantLock lock_batch = new ReentrantLock();

    private FilterItem[] batch_items;

    public Filter(Context context) {
        this(context, 5);
    }

    public Filter(Context context, int numAllocation) {
        this.render_script = RenderScript.create(context);
        this.script = new ScriptC_imgproc(render_script);

        this.init(numAllocation);
    }

    public void init(int numAllocation) {
        this.batch_items = null;

        this.allocation_input_yuv = null;
        this.allocations = new Allocation[numAllocation];
        for(int i = 0; i < numAllocation; i++) {
            this.allocations[i] = null;
        }
        this.num_allocation = numAllocation;
        this.index_allocation = -1;
        this.allocation_context = null;
    }

    public void setData(Bitmap source) {
        Bitmap image0 = source.copy(source.getConfig(), true);
        this.allocation_context = Allocation.createFromBitmap(this.render_script, image0);

        Bitmap images[] = new Bitmap[this.num_allocation];
        for(int i = 0; i < this.num_allocation; i++) {
            images[i] = source.copy(source.getConfig(), true);
            this.allocations[i] = Allocation.createFromBitmap(this.render_script, images[i]);
        }
        this.index_allocation = 0;

        this.height = source.getHeight();
        this.width = source.getWidth();

        this.script.set_height(this.height);
        this.script.set_width(this.width);

        this.id_channel = -1;
    }

    public void setDataFromYUV(byte[] data) {
        this.allocation_input_yuv.copyFrom(data);
        this.setDataFromYUVDirect();
    }

    public void setDataFromYUVDirect() {
        this.lock.lock();

        this.script.set_context(this.allocation_input_yuv);
        this.script.forEach_yvu2rgb(this.allocation_context, this.allocations[this.index_allocation]);

        this.id_channel = -1;
        this.lock.unlock();
    }

    public void prepareForYUV(Size size) {
        this.width = size.getHeight();
        this.height = size.getWidth();

        this.script.set_width(this.width);
        this.script.set_height(this.height);

        /*Type.Builder type_yuv = new Type.Builder(this.render_script, Element.U8(this.render_script))
                .setX(this.height).setY(this.width).setYuvFormat(ImageFormat.NV21);
        this.allocation_input_yuv = Allocation.createTyped(this.render_script,
                type_yuv.create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);*/
        this.allocation_input_yuv = Allocation.createSized(this.render_script, Element.I8(this.render_script),
                this.height * this.width * 3 / 2, Allocation.USAGE_SCRIPT);

        Type.Builder type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.width).setY(this.height);
        this.allocation_context = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
        for(int i = 0; i < this.num_allocation; i++) {
            this.allocations[i] = Allocation.createTyped(this.render_script,
                    type_rgba.create(), Allocation.USAGE_SCRIPT);
        }
        this.index_allocation = 0;
    }

    public Allocation getInputAllocation() { return this.allocation_input_yuv; }

    public void setDataFromBitmap(Bitmap image) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        this.allocation_output_rgba.copyFrom(image);
        this.script.set_context(this.allocation_output_rgba);
        this.script.forEach_transpose(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.script.set_context(this.allocations[next_index]);
        this.script.set_width(this.width);
        this.script.forEach_flip_vertical(
                this.allocations[next_index], this.allocations[this.index_allocation]);

        this.id_channel = -1;
        this.lock.unlock();
    }

    public void prepareForBitmap(Size size) {
        this.width = size.getHeight();
        this.height = size.getWidth();

        this.script.set_width(this.width);
        this.script.set_height(this.height);

        Type.Builder type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.width).setY(this.height);
        this.allocation_context = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
        for(int i = 0; i < this.num_allocation; i++) {
            this.allocations[i] = Allocation.createTyped(this.render_script,
                    type_rgba.create(), Allocation.USAGE_SCRIPT);
        }
        this.index_allocation = 0;

        type_rgba = new Type.Builder(this.render_script,
                Element.RGBA_8888(this.render_script)).setX(this.height).setY(this.width);
        this.allocation_output_rgba = Allocation.createTyped(this.render_script,
                type_rgba.create(), Allocation.USAGE_SCRIPT);
    }

    public boolean ifNotReadyForVideoInput() {
        return this.allocation_input_yuv == null || this.index_allocation < 0;
    }

    public void resetData() {
        for(int i = 0; i < this.num_allocation; i++) {
            this.allocations[i] = null;
        }
        this.index_allocation = 0;
        this.allocation_context = null;
    }

    // this function is for changing channel or decode single channel
    public void setContext(Bitmap context) {
        if(context.getHeight() != this.height || context.getWidth() != this.width) {
            context = ThumbnailUtils.extractThumbnail(context, this.width, this.height);
        }

        this.allocation_context.copyFrom(context);
    }

    public void preprocess(int index) {
        if(index < 0 || index > 6) index = 0;
        // init
        if(this.id_channel < 0) {
            this.script.set_index_channel(index);
            this.allocation_context.copyFrom(this.allocations[this.index_allocation]);
            this.script.forEach_encode(
                    this.allocation_context, this.allocations[this.index_allocation]);
        }
        // switch channel, this.allocation_context should be the original image
        else if(this.id_channel != index) {
            int next_index = (this.index_allocation + 1) % this.num_allocation;
            this.script.set_context(this.allocation_context);
            this.script.set_index_channel(this.id_channel);
            this.script.forEach_decode_with_context(
                    this.allocations[this.index_allocation], this.allocations[next_index]);
            this.script.set_index_channel(index);
            this.script.forEach_encode(
                    this.allocations[next_index], this.allocations[this.index_allocation]);
        }

        this.id_channel = index;
    }

    public void doRGB2BW() {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        this.script.forEach_rgb_to_bw(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    public void doRescale(float scale, int idChannel) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        this.preprocess(idChannel);

        this.script.set_scale(scale);
        this.script.forEach_rescale(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    /*public void doUpPyramid() {
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
    }*/

    public void doGaussian(int radius, int idChannel, float sigma) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        float[] mask = this.createGaussianMask(radius, sigma);
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprocess(idChannel);

        this.script.set_scale(1.0f);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocations[this.index_allocation]);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    public void doLaplacian(int idChannel, float scale) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        float[] mask = this.createLaplacianMask();
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprocess(idChannel);

        this.script.set_scale(scale);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocations[this.index_allocation]);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(1);
        this.script.forEach_gaussian(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    public void doGaussianLaplacian(int radius, int idChannel, float sigma, float scale) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        float[] mask = this.createGaussianLaplacianMask(radius, sigma);
        for(int i = 0; i < mask.length; i++) {
            mask[i] *= scale;
        }
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprocess(idChannel);

        this.script.set_scale(1.0f);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocations[this.index_allocation]);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    public void doMean(int radius, int idChannel) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        this.preprocess(idChannel);

        this.script.set_context(this.allocations[this.index_allocation]);
        this.script.set_radius(radius);
        this.script.forEach_mean(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    public void doBilateral(int radius, int idChannel, float sigma_spatial, float sigma_range) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        float[] mask = this.createGaussianMask(radius, sigma_spatial);
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.preprocess(idChannel);

        this.script.set_scale(1.0f);
        this.script.set_threshold_value(2.0f * sigma_range * sigma_range);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocations[this.index_allocation]);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_bilateral(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.lock.unlock();
    }

    public void doThreshold(int idChannel, float thresholdValue) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        this.preprocess(idChannel);

        this.script.set_threshold_value(thresholdValue);
        this.script.set_index_channel(idChannel);
        this.script.forEach_threshold(
                this.allocations[this.index_allocation], this.allocations[next_index]);
        this.index_allocation = next_index;

        this.id_channel = 0;

        this.lock.unlock();
    }

    public void doSobol(int idChannel, int direction) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        this.lock.lock();

        this.preprocess(idChannel);

        this.script.set_index_channel(idChannel);
        this.script.set_context(this.allocations[this.index_allocation]);
        switch (direction) {
            // in x direction
            case 1:
                this.script.forEach_sobol_operator_x(
                        this.allocations[this.index_allocation], this.allocations[next_index]);
                break;
            // in y direction
            case 2:
                this.script.forEach_sobol_operator_y(
                        this.allocations[this.index_allocation], this.allocations[next_index]);
                break;
            // in both
            default:
                int diff_x = (this.index_allocation + 2) % this.num_allocation;
                int diff_y = (this.index_allocation + 3) % this.num_allocation;
                this.script.forEach_sobol_operator_x(
                        this.allocations[this.index_allocation], this.allocations[diff_x]);
                this.script.forEach_sobol_operator_y(
                        this.allocations[this.index_allocation], this.allocations[diff_y]);
                this.script.set_context(this.allocations[diff_y]);
                this.script.forEach_magnitude(this.allocations[diff_x], this.allocations[next_index]);
        }

        this.index_allocation = next_index;
        this.id_channel = 0;

        this.lock.unlock();
    }

    public void doHarrisDetect(float alpha) {
        int next_index = (this.index_allocation + 1) % this.num_allocation;
        int index_diff_x = (this.index_allocation + 2) % this.num_allocation;
        int index_diff_y = (this.index_allocation + 3) % this.num_allocation;
        this.lock.lock();

        this.preprocess(this.id_channel);

        this.script.set_scale(alpha);
        this.script.set_context(this.allocations[this.index_allocation]);

        this.script.forEach_sobol_operator_x(
                this.allocations[this.index_allocation], this.allocations[index_diff_x]);
        this.script.forEach_sobol_operator_y(
                this.allocations[this.index_allocation], this.allocations[index_diff_y]);

        // do smoothing later
        this.script.set_context(this.allocations[index_diff_y]);
        this.script.forEach_harris_detector(this.allocations[index_diff_x], this.allocations[next_index]);

        this.index_allocation = next_index;
        this.id_channel = 0;

        this.lock.unlock();
    }

    public void copyCurrentWithContext(Bitmap image) {
        if(this.id_channel >= 0) {
            int next_index = (this.index_allocation + 1) % this.num_allocation;
            this.script.set_index_channel(this.id_channel);
            this.script.set_context(this.allocation_context);
            this.script.forEach_decode_with_context(
                    this.allocations[this.index_allocation], this.allocations[next_index]);
            this.allocations[next_index].copyTo(image);
        }
        else {
            this.allocations[this.index_allocation].copyTo(image);
        }
    }

    public void copyCurrent(Bitmap image) {
        if(this.id_channel >= 0) {
            int next_index = (this.index_allocation + 1) % this.num_allocation;
            this.script.set_index_channel(this.id_channel);
            this.script.forEach_decode(
                    this.allocations[this.index_allocation], this.allocations[next_index]);
            this.allocations[next_index].copyTo(image);
        }
        else {
            this.allocations[this.index_allocation].copyTo(image);
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
        return new float[]{-0.5f, -1.0f, -0.5f, -1.0f, 6.0f, -1.0f, -0.5f, -1.0f, -0.5f};
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
                    // case "up_pyramid":
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
                        break;
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
                    case "sobol":
                        this.batch_items[i] = new FilterItem(name, 2, 0);
                        this.batch_items[i].int_params[0] = json_obj.getInt("channel");
                        this.batch_items[i].int_params[1] = json_obj.getInt("direction");
                        break;
                    case "feature_detector_harris":
                        this.batch_items[i] = new FilterItem(name, 0, 1);
                        this.batch_items[i].float_params[0] = (float)json_obj.getDouble("alpha");
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
                    // case "up_pyramid":
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
                    case "sobol":
                        this.doSobol(item.int_params[0], item.int_params[1]);
                        break;
                    case "feature_detector_harris":
                        this.doHarrisDetect(item.float_params[0]);
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
