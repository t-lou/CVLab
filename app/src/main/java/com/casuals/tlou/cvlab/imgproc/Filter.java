package com.casuals.tlou.cvlab.imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.FloatMath;

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
    private String[] available_filters = {"rgb_to_bw", "gaussian", "mean"};

    private RenderScript render_script;
    private ScriptC_imgproc script;
    private Bitmap data;

    private Allocation allocation_in;
    private Allocation allocation_out;

    private boolean if_colorful;

    public Filter(Context context) {
        this.render_script = RenderScript.create(context);
        this.script = new ScriptC_imgproc(render_script);

        this.if_colorful = true;
    }

    public void setData(Bitmap source) {
        this.data = source.copy(source.getConfig(), true);

        this.allocation_in = Allocation.createFromBitmap(this.render_script, this.data);
        this.allocation_out = Allocation.createFromBitmap(this.render_script, this.data);
    }

    public void doRGB2BW() {
        if(this.if_colorful) {
            this.script.forEach_rgb_to_bw(this.allocation_in, this.allocation_out);
        }
        this.if_colorful = false;
    }

    public void doGaussian(int radius, int idChannel, float sigma) {
        float[] mask = this.createGaussianMask(radius, sigma);
        Allocation allocation_mask = Allocation.createSized(this.render_script,
                Element.F32(this.render_script), mask.length);
        allocation_mask.copyFrom(mask);

        this.script.set_mask(allocation_mask);
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.data.getHeight());
        this.script.set_width(this.data.getWidth());
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_gaussian(this.allocation_in, this.allocation_out);
    }

    public void doMean(int radius, int idChannel) {
        this.script.set_context(this.allocation_in);
        this.script.set_height(this.data.getHeight());
        this.script.set_width(this.data.getWidth());
        this.script.set_if_bw(!this.if_colorful);
        this.script.set_index_channel(idChannel);
        this.script.set_radius(radius);
        this.script.forEach_mean(this.allocation_in, this.allocation_out);
    }

    //public void getOutput() {
    //    this.allocation_out.copyTo(this.data);
    //}

    public Bitmap getCurrent() {
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
}
