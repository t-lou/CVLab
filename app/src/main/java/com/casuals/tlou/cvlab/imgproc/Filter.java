package com.casuals.tlou.cvlab.imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;

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
    private String[] available_filters = {"rgb_to_bw", "rescale", "up_pyramid",
            "gaussian", "laplacian", "gaussian_laplacian", "mean", "bilateral",
            "threshold"};

    private RenderScript render_script;
    private ScriptC_imgproc script;

    private Allocation allocation_in;
    private Allocation allocation_out;
    private Allocation allocation_context;

    private int id_channel;
    private int height, width;

    private final ReentrantLock lock = new ReentrantLock();

    public Filter(Context context) {
        this.render_script = RenderScript.create(context);
        this.script = new ScriptC_imgproc(render_script);
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

        // negative: unassigned
        this.id_channel = -1;
    }

    public void resetData() {
        this.allocation_in = null;
        this.allocation_out = null;
        this.allocation_context = null;
    }

    public void preprecess(int index) {
        // init
        if(this.id_channel < 0) {
            this.script.set_index_channel(index);
            this.allocation_context.copyFrom(this.allocation_in);
            this.script.forEach_encode(this.allocation_context, this.allocation_in);
        }
        // switch channel
        else if(this.id_channel != index) {
            this.script.set_context(this.allocation_context);
            this.script.set_index_channel(this.id_channel);
            this.script.forEach_decode(this.allocation_in, this.allocation_out);
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

    public void copyCurrent(Bitmap image) {
        if(this.id_channel >= 0) {
            this.allocation_context = Allocation.createFromBitmap(this.render_script, image);
            this.script.set_index_channel(this.id_channel);
            this.script.set_context(this.allocation_context);
            this.script.forEach_decode(this.allocation_in, this.allocation_out);
        }
        this.allocation_out.copyTo(image);
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
}
