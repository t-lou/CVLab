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
            "gaussian", "laplacian", "gaussian_laplacian", "mean", "threshold"};

    private RenderScript render_script;
    private ScriptC_imgproc script;
    private Bitmap data;
    private Bitmap data_out;

    private Allocation allocation_in;
    private Allocation allocation_out;

    private boolean if_colorful;

    private final ReentrantLock lock = new ReentrantLock();

    public Filter(Context context) {
        this.render_script = RenderScript.create(context);
        this.script = new ScriptC_imgproc(render_script);

        this.if_colorful = true;
    }

    public void setData(Bitmap source) {
        this.data = source.copy(source.getConfig(), true);
        //this.data_out = source.copy(source.getConfig(), true);

        this.allocation_in = Allocation.createFromBitmap(this.render_script, this.data);
        //this.allocation_out = Allocation.createFromBitmap(this.render_script, this.data_out);
        this.allocation_out = Allocation.createFromBitmap(this.render_script, this.data);
    }

    public void doRGB2BW() {
        this.lock.lock();

        if(this.if_colorful) {
            this.script.forEach_rgb_to_bw(this.allocation_in, this.allocation_out);
            //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);
        }
        this.if_colorful = false;

        this.lock.unlock();
    }

    public void doRescale(float scale, int idChannel) {
        this.lock.lock();

        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_scale(scale);
        this.script.forEach_rescale(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doUpPyramid() {
        int width = this.data.getWidth(), height = this.data.getHeight();
        this.lock.lock();

        this.script.set_context(this.allocation_in);
        this.data = null;
        this.data = Bitmap.createBitmap(width / 2, height / 2, this.data_out.getConfig());
        this.data_out = null;
        this.data_out = Bitmap.createBitmap(width / 2, height / 2, this.data.getConfig());
        this.allocation_in = Allocation.createFromBitmap(this.render_script, this.data);
        this.allocation_out = Allocation.createFromBitmap(this.render_script, this.data_out);
        this.script.forEach_up_pyramid(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doGaussian(int radius, int idChannel, float sigma) {
        float[] mask = this.createGaussianMask(radius, sigma);
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.lock.lock();

        this.script.set_scale(1.0f);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.data.getHeight());
        this.script.set_width(this.data.getWidth());
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doLaplacian(int idChannel, float scale) {
        float[] mask = this.createLaplacianMask();
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.lock.lock();

        this.script.set_scale(scale);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.data.getHeight());
        this.script.set_width(this.data.getWidth());
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(1);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doGaussianLaplacian(int radius, int idChannel, float sigma, float scale) {
        float[] mask = this.createGaussianLaplacianMask(radius, sigma);
        for(int i = 0; i < mask.length; i++) {
            mask[i] *= scale;
        }
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.lock.lock();

        this.script.set_scale(1.0f);
        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.data.getHeight());
        this.script.set_width(this.data.getWidth());
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doMean(int radius, int idChannel) {
        this.lock.lock();

        this.script.set_context(this.allocation_in);
        this.script.set_height(this.data.getHeight());
        this.script.set_width(this.data.getWidth());
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_mean(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    public void doThreshold(int idChannel, float thresholdValue) {
        this.lock.lock();

        this.script.set_threshold_value(thresholdValue);
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.forEach_threshold(this.allocation_in, this.allocation_out);
        //this.script.forEach_out_to_in(this.allocation_out, this.allocation_in);

        this.lock.unlock();
    }

    //public void getOutput() {
    //    this.allocation_out.copyTo(this.data);
    //}

    public Bitmap getCurrent() {
        //this.allocation_out.copyTo(this.data_out);
        //return this.data_out;
        return this.data;
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
        float[] mask = {0.5f, 1.0f, 0.5f, 1.0f, -6.0f, 1.0f, 0.5f, 1.0f, 0.5f};
        return mask;
    }

    private float[] createGaussianLaplacianMask(int radius, float sigma) {
        int length = 2 * radius + 1;
        float[] mask_gaussian = this.createGaussianMask(radius, sigma);
        float[] mask_laplacian = this.createGaussianMask(radius, sigma);
        float[] mask = new float[length * length];
        int index = 0;

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
                    }
                }
                index++;
            }
        }

        return mask;
    }

    public void waitTillEnd() {
        this.lock.lock();
        this.lock.unlock();
    }
}
