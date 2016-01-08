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

union CONVERTOR {
    float _float;
    uchar4 _uchar4;
};

rs_allocation context;
rs_allocation mask;
bool if_bw;
int index_channel;
int width, height;
int radius;
float scale;
float threshold_value;

uchar4 __attribute__((kernel)) copy(uchar4 in) {
    return in;
}

// uchar4 -> float
uchar4 __attribute__((kernel)) encode(uchar4 in) {
    float result = 0.0f;
    union CONVERTOR convertor;
    float4 input = rsUnpackColor8888(in);
    // grey scale
    if(index_channel <= 0 || index_channel >= 7) {
        result = 0.2126f * input.x + 0.7152f * input.y + 0.0722f * input.z;
    }
    // R, G, B
    else if(index_channel < 4) {
        result = input[index_channel - 1];
    }
    // H, S, V
    else if(index_channel < 7) {
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

        result = hsv[index_channel - 4];
    }

    convertor._float = result;
    return convertor._uchar4;
}

// float -> uchar4
uchar4 __attribute__((kernel)) decode(uchar4 in, uint32_t x, uint32_t y) {
    union CONVERTOR convertor;
    float4 pixel;

    convertor._uchar4 = in;
    // value to gray scale
    if(index_channel <= 0 || index_channel >= 7) {
        if(convertor._float < 0.0f) {
            convertor._float = 0.0f;
        }
        else if(convertor._float > 1.0f) {
            convertor._float = 1.0f;
        }

        pixel = convertor._float;
        //pixel.w = 1.0f;
    }
    // value to R, G or B
    else if (index_channel < 4) {
        pixel = rsUnpackColor8888(rsGetElementAt_uchar4(context, x, y));
        if(convertor._float < 0.0f) {
            convertor._float = 0.0f;
        }
        else if(convertor._float > 1.0f) {
            convertor._float = 1.0f;
        }

        pixel[index_channel - 1] = convertor._float;
    }
    // value to H, S, V
    else {
        // this should be more complicated than I expect, so do it later
        pixel = (float4)(1.0f, 1.0f, 1.0f, 1.0f);
        /*float4 input = rsUnpackColor8888(rsGetElementAt_uchar4(context, x, y));
        float4 rgb;
        int i;
        float f, p, q, t;

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
        }*/
    }
    return rsPackColorTo8888(pixel);
}

uchar4 __attribute__((kernel)) rgb_to_bw(uchar4 in) {
    float4 f4 = rsUnpackColor8888(in);
    float value = 0.2126f * f4.x + 0.7152f * f4.y + 0.0722f * f4.z;
    f4.xyz = value;
    f4.w = 1.0f;
    return rsPackColorTo8888(f4);
}

uchar4 __attribute__((kernel)) up_pyramid(uchar4 in, uint32_t x, uint32_t y) {
    union CONVERTOR value;
    if(index_channel < 0) {
        int4 tmp = (int4)(0, 0, 0, 0);
        uchar4 neighbor;
        neighbor = rsGetElementAt_uchar4(context, x * 2, y * 2);
        tmp.x += (int)neighbor.x; tmp.y += (int)neighbor.y;
        tmp.z += (int)neighbor.z; tmp.w += (int)neighbor.w;
        neighbor = rsGetElementAt_uchar4(context, x * 2 + 1, y * 2);
        tmp.x += (int)neighbor.x; tmp.y += (int)neighbor.y;
        tmp.z += (int)neighbor.z; tmp.w += (int)neighbor.w;
        neighbor = rsGetElementAt_uchar4(context, x * 2, y * 2 + 1);
        tmp.x += (int)neighbor.x; tmp.y += (int)neighbor.y;
        tmp.z += (int)neighbor.z; tmp.w += (int)neighbor.w;
        neighbor = rsGetElementAt_uchar4(context, x * 2 + 1, y * 2 + 1);
        tmp.x += (int)neighbor.x; tmp.y += (int)neighbor.y;
        tmp.z += (int)neighbor.z; tmp.w += (int)neighbor.w;

        value._uchar4.x = (uchar)(tmp.x / 4);
        value._uchar4.y = (uchar)(tmp.y / 4);
        value._uchar4.z = (uchar)(tmp.z / 4);
        value._uchar4.w = (uchar)(tmp.w / 4);
    }
    else {
        union CONVERTOR neighbor;
        value._float = 0.0f;
        neighbor._uchar4 = rsGetElementAt_uchar4(context, x * 2, y * 2);
        value._float += neighbor._float;
        neighbor._uchar4 = rsGetElementAt_uchar4(context, x * 2 + 1, y * 2);
        value._float += neighbor._float;
        neighbor._uchar4 = rsGetElementAt_uchar4(context, x * 2, y * 2 + 1);
        value._float += neighbor._float;
        neighbor._uchar4 = rsGetElementAt_uchar4(context, x * 2 + 1, y * 2 + 1);
        value._float += neighbor._float;
        value._float /= 4.0f;
    }
    return value._uchar4;
}

uchar4 __attribute__((kernel)) rescale(uchar4 in) {
    union CONVERTOR value;
    value._uchar4 = in;
    value._float *= scale;
    return value._uchar4;
}

uchar4 __attribute__((kernel)) gaussian(uchar4 in, uint32_t x, uint32_t y) {
    int i, j, index = 0;
    union CONVERTOR value, neighbor;

    value._float = 0.0f;
    for(i = -radius; i <= radius; i++) {
        for(j = -radius; j <= radius; j++) {
            if((x + i >= 0) && (x + i < width) && (y + j >= 0) && (y + j < height)) {
                neighbor._uchar4 = rsGetElementAt_uchar4(context, x + i, y + j);
                value._float += neighbor._float * rsGetElementAt_float(mask, index);
            }
            index++;
        }
    }
    return value._uchar4;
}

uchar4 __attribute__((kernel)) mean(uchar4 in, uint32_t x, uint32_t y) {
    int i, j;
    union CONVERTOR value, neighbor;
    float weight = 0.0f;

    value._float = 0.0f;
    for(i = -radius; i <= radius; i++) {
        for(j = -radius; j <= radius; j++) {
            if((x + i >= 0) && (x + i < width) && (y + j >= 0) && (y + j < height)) {
                neighbor._uchar4 = rsGetElementAt_uchar4(context, x + i, y + j);
                value._float += neighbor._float;
                weight += 1.0f;
            }
        }
    }
    value._float /= weight;
    return value._uchar4;
}

uchar4 __attribute__((kernel)) bilateral(uchar4 in, uint32_t x, uint32_t y) {
    // use threshold_value as 2 * sigma^2) for range kernel
    int i, j, index = 0;
    float weight = 0.0f, weight_range;
    union CONVERTOR value, neighbor, original;

    value._float = 0.0f;
    original._uchar4 = in;

    for(i = -radius; i <= radius; i++) {
        for(j = -radius; j <= radius; j++) {
            if((x + i >= 0) && (x + i < width) && (y + j >= 0) && (y + j < height)) {
                neighbor._uchar4 = rsGetElementAt_uchar4(context, x + i, y + j);

                weight_range = neighbor._float - original._float;
                weight_range = exp(-weight_range * weight_range / threshold_value);

                value._float += neighbor._float
                    * rsGetElementAt_float(mask, index) * weight_range;
                weight += rsGetElementAt_float(mask, index) * weight_range;
            }
            index++;
        }
    }
    value._float /= weight;

    return value._uchar4;
}

uchar4 __attribute__((kernel)) threshold(uchar4 in) {
    union CONVERTOR value;
    value._uchar4 = in;
    if(value._float < threshold_value) {
        value._float = 0.0f;
    }
    else {
        if(index_channel < 4) {
            value._float = 1.0f;
        }
        else {
            value._float = 1.0f;
        }
    }
    return value._uchar4;
}
