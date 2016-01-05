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
float scale;
float threshold_value;

/*float3 __attribute__((kernel)) rgb_to_hsv(float3 input) {
    float3 hsv;
    float c_min = 2.0f, c_max = -1.0f, delta;
    int id_max;
    for(int i = 0; i < 3; i++) {
        if(c_max < input[i]) {
            id_max = i;
            c_max = input[i];
        }
        if(c_min > input[i]) {
            c_max = input[i];
        }
    }
    c_max = max(input[0], max(input[1], input[2]));
    c_min = min(input[0], min(input[1], input[2]));
    delta = c_max - c_min;

    if(delta < 0.000000001f || c_max < 0.000000001f) {
        hsv[0] = 0.0f;
        hsv[1] = 0.0f;
        hsv[2] = c_max;
    }
    else {
        hsv[0] = 60.0f * (((float)id_max) * 2.0f
            * (float)(input[(id_max + 1) % 3] - input[(id_max + 2) % 3]) / delta);
        if(hsv[0] < 0.0f) {
            hsv[0] += 360.0f;
        }
        hsv[1] = delta / c_max;
        hsv[2] = c_max;
    }

    return hsv;
}

float3 __attribute__((kernel)) hsv_to_rgb(float3 input) {
    float3 rgb;
    int i;
    float f, p, q, t;
    float3 tmp;

    if( input[1] < 0.000000001f ) {
    	rgb = input[1];
    }
    else {
    	input[0] /= 60.0f;
    	i = (int)floor(input[0]);
    	f = input[0] - floor(input[0]);

    	p = input[2] * (1.0f - input[1]);
    	q = input[2] * (1.0f - input[1] * f);
    	t = input[2] * (1.0f - input[1] * (1.0f - f));

        if(i % 2 == 0) {
            tmp = (float3)(input[2], t, p);
            i /= 2;
            rgb[0] = tmp[i];
            rgb[1] = tmp[(i + 1) % 3];
            rgb[2] = tmp[(i + 2) % 3];
        }
        else {
            tmp = (float3)(q, input[2], p);
            i /= 2;
            rgb[0] = tmp[i];
            rgb[1] = tmp[(i + 1) % 3];
            rgb[2] = tmp[(i + 2) % 3];
        }
    }
    return rgb;
}*/

uchar4 __attribute__((kernel)) out_to_in(uchar4 in) {
    return in;
}

uchar4 __attribute__((kernel)) rgb_to_bw(uchar4 in) {
    float4 f4 = rsUnpackColor8888(in);
    float value = 0.2126f * f4.x + 0.7152f * f4.y + 0.0722f * f4.z;
    f4.xyz = value;
    f4.w = 1.0f;
    return rsPackColorTo8888(f4);
}

uchar4 __attribute__((kernel)) up_pyramid(uchar4 in, uint32_t x, uint32_t y) {
    uchar4 out = (uchar4)(0, 0, 0, 0);
    out += rsGetElementAt_uchar4(context, x * 2, y * 2) / 4;
    out += rsGetElementAt_uchar4(context, x * 2 + 1, y * 2) / 4;
    out += rsGetElementAt_uchar4(context, x * 2, y * 2 + 1) / 4;
    out += rsGetElementAt_uchar4(context, x * 2 + 1, y * 2 + 1) / 4;
    return out;
}

uchar4 __attribute__((kernel)) rescale(uchar4 in) {
    float4 f4 = rsUnpackColor8888(in);
    if(if_bw || index_channel < 0 || index_channel >= 4) {
        f4 *= scale;
    }
    else {
        f4[index_channel] *= scale;
    }
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
    out.x = fabs(out.x);
    out.y = fabs(out.y);
    out.z = fabs(out.z);
    out.w = fabs(out.w);
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

uchar4 __attribute__((kernel)) bilateral(uchar4 in, uint32_t x, uint32_t y) {
    // use threshold_value as 2 * sigma^2) for range kernel
    int i, j, index = 0;
    float4 out = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
    float4 origin = rsUnpackColor8888(in);
    float value, weight = 0.0f, weight_range;
    if(if_bw || index_channel < 0 || index_channel >= 4) {
        value = 0.2126f * origin.x + 0.7152f * origin.y + 0.0722f * origin.z;
    }
    else {
        value = origin[index_channel];
    }

    for(i = -radius; i <= radius; i++) {
        for(j = -radius; j <= radius; j++) {
            if(if_bw || index_channel < 0 || index_channel >= 4) {
                weight_range = 0.2126f * origin.x + 0.7152f * origin.y + 0.0722f * origin.z;
            }
            else {
                weight_range = origin[index_channel];
            }

            if((x + i >= 0) && (x + i < width) && (y + j >= 0) && (y + j < height)) {
                weight_range = exp(-weight_range * weight_range / threshold_value);
                out += rsUnpackColor8888(rsGetElementAt_uchar4(context, x + i, y + j))
                    * rsGetElementAt_float(mask, index) * weight_range;
                weight += rsGetElementAt_float(mask, index) * weight_range;
            }
            index++;
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

uchar4 __attribute__((kernel)) threshold(uchar4 in) {
    bool if_over = false;
    float4 result;
    float4 f4 = rsUnpackColor8888(in);
    if(if_bw || index_channel < 0 || index_channel >= 4) {
    // if(if_bw || index_channel < 0 || index_channel >= 7) {
        float value = 0.2126f * f4.x + 0.7152f * f4.y + 0.0722f * f4.z;
        if_over = value > threshold_value;
    }
    else if(index_channel < 4) {
        // r, g, b or alpha
        if_over = f4[index_channel] > threshold_value;
    }
    // else {
        // h, s or v
        // if_over = rgb_to_hsv(f4.xyz)[index_channel - 4] > threshold_value;
    // }

    if(if_over) {
        result = (float4)(1.0f, 1.0f, 1.0f, 1.0f);
    }
    else {
        result = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
    }
    return rsPackColorTo8888(result);
}