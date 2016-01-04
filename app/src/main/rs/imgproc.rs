#pragma version(1)
#pragma rs java_package_name(com.casuals.tlou.cvlab.imgproc)
#pragma rs_fp_full

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

rs_allocation context;
rs_allocation mask;
bool if_bw;
int index_channel;
int width, height;
int radius;

uchar4 __attribute__((kernel)) rgb_to_bw(uchar4 in) {
    float4 f4 = rsUnpackColor8888(in);
    float value = 0.2126f * f4.x + 0.7152f * f4.y + 0.0722f * f4.z;
    f4.xyz = value;
    f4.w = 1.0f;
    return rsPackColorTo8888(f4);
}

uchar4 __attribute__((kernel)) gaussian(uchar4 in, uint32_t x, uint32_t y) {
    int i, j, index = 0;
    float4 out = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
    float4 origin = rsUnpackColor8888(in);
    for(i = -radius; i <= radius; i++) {
        for(j = -radius; j <= radius; j++) {
            if((x + i >= 0) && (x + i < width) && (y + j >= 0) && (y + j < height)) {
                out += rsUnpackColor8888(rsGetElementAt_uchar4(context, x + i, y + j))
                    * rsGetElementAt_float(mask, index);
            }
            index++;
        }
    }
    if(if_bw || index_channel < 0 || index_channel >= 4) {
        return rsPackColorTo8888(out);
    }
    else {
        origin[index_channel] = out[index_channel];
        return rsPackColorTo8888(origin);
    }
}

uchar4 __attribute__((kernel)) mean(uchar4 in, uint32_t x, uint32_t y) {
    int i, j;
    float4 out = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
    float weight = 0.0f;
    float4 origin = rsUnpackColor8888(in);
    for(i = -radius; i <= radius; i++) {
        for(j = -radius; j <= radius; j++) {
            if((x + i >= 0) && (x + i < width) && (y + j >= 0) && (y + j < height)) {
                out += rsUnpackColor8888(rsGetElementAt_uchar4(context, x + i, y + j));
                weight += 1.0f;
            }
        }
    }
    out /= weight;
    if(if_bw || index_channel < 0 || index_channel >= 4) {
        return rsPackColorTo8888(out);
    }
    else {
        origin[index_channel] = out[index_channel];
        return rsPackColorTo8888(origin);
    }
}