package com.casuals.tlou.cvlab.imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

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
    private String[] available_filters = {"rgb_to_bw", "gaussian"};

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

    public void doGaussian() {
        if(this.if_colorful) {
            this.script.forEach_rgb_to_bw(this.allocation_in, this.allocation_out);
        }
        this.if_colorful = false;
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
}
