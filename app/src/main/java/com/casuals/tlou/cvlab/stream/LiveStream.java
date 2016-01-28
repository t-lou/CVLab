/*
 * Copyright 2016 Tongxi Lou
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.casuals.tlou.cvlab.stream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.imgproc.Filter;
import com.casuals.tlou.cvlab.main;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class LiveStream extends Activity implements View.OnClickListener {

    private TextView debug;
    private TextView info_display;
    private long last_time_millisec;
    private float[] time_intervals;
    private int time_interval_count;

    private Size size_preview;
    private Size size_image;
    private Size[] size_image_list;
    private String[] size_image_list_str;
    private TextureView camera_preview;
    private ImageView canvas;
    private Bitmap current_image;
    private Filter filter;
    private Button button_load_batch;
    private Button button_back;
    private Button button_sel_resolution;

    private TextureView.SurfaceTextureListener camera_preview_listener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            openCamera();
            setPreviewTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            setPreviewTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    // camera dev and info
    private String camera_id_default;
    private CameraDevice camera_dev;
    private boolean if_camera_ready_for_next;
    private Semaphore camera_lock = new Semaphore(1);
    private CameraDevice.StateCallback camera_dev_callback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice dev) {
            camera_lock.release();
            camera_dev = dev;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice dev) {
            camera_lock.release();
            dev.close();
            camera_dev = null;
        }

        @Override
        public void onError(CameraDevice dev, int error) {
            camera_lock.release();
            dev.close();
            camera_dev = null;
        }
    };

    private CaptureRequest preview_capture_request;
    private CaptureRequest.Builder preview_capture_request_builder;
    private CameraCaptureSession camera_capture_session;
    private CameraCaptureSession.CaptureCallback camera_capture_session_callback
            = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                     long timestamp, long frame_number) {
            super.onCaptureStarted(session, request, timestamp, frame_number);
        }
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture surface_texture = this.camera_preview.getSurfaceTexture();
            assert surface_texture != null;

            surface_texture.setDefaultBufferSize(this.size_preview.getWidth(), this.size_preview.getHeight());

            Surface preview_surface = new Surface(surface_texture);
            this.preview_capture_request_builder =
                    this.camera_dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            this.preview_capture_request_builder.addTarget(preview_surface);
            this.preview_capture_request_builder.addTarget(this.image_reader.getSurface());
            this.camera_dev.createCaptureSession(Arrays.asList(preview_surface, this.image_reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (camera_dev == null) {
                                return;
                            }
                            try {
                                camera_capture_session = session;
                                preview_capture_request_builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                preview_capture_request = preview_capture_request_builder.build();
                                camera_capture_session.setRepeatingRequest(preview_capture_request,
                                        camera_capture_session_callback, background_handler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {}
                    }, null);
        }
        catch (CameraAccessException e) {
        }
    }

    private HandlerThread background_handler_thread;
    private Handler background_handler;

    private void openBackgroundThread() {
        this.background_handler_thread = new HandlerThread("CameraBackground");
        this.background_handler_thread.start();
        this.background_handler = new Handler(this.background_handler_thread.getLooper());
    }
    private void closeBackgoundThread() {
        this.background_handler_thread.quitSafely();
        try {
            this.background_handler_thread.join();
            this.background_handler_thread = null;
            this.background_handler = null;
        } catch (InterruptedException e) {
        }
    }

    private void setPreviewTransform(int view_width, int view_height) {
        Matrix tf = new Matrix();
        float scale_x = ((float)size_preview.getHeight()) / ((float)view_width),
                scale_y = ((float)size_preview.getWidth()) / ((float)view_height),
                scaler = (scale_x > scale_y)?scale_x:scale_y;

        scale_x /= scaler;
        scale_y /= scaler;

        tf.setScale(scale_x, scale_y);
        this.camera_preview.setTransform(tf);
    }

    private void setupCamera(int width, int height) {
        CameraManager camera_manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        try {
            StreamConfigurationMap config;
            for(String str : camera_manager.getCameraIdList()) {
                CameraCharacteristics chars = camera_manager.getCameraCharacteristics(str);
                if(chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
                    if(this.camera_id_default.length() == 0) {
                        this.camera_id_default = str;
                        config = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                        this.size_preview = this.getPreferedPreviewSize(
                                config.getOutputSizes(SurfaceTexture.class), width, height);
                        this.size_image = this.getPreferedPreviewSize(
                                config.getOutputSizes(SurfaceTexture.class),
                                this.canvas.getWidth(), this.canvas.getHeight());
                        this.size_image_list = config.getOutputSizes(SurfaceTexture.class);
                        this.size_image_list_str = new String[this.size_image_list.length];
                        for(int i = 0; i < this.size_image_list.length; i++) {
                            this.size_image_list_str[i] = this.size_image_list[i].getHeight()
                                    + "x" + this.size_image_list[i].getWidth();
                        }
                        this.button_sel_resolution.setText(this.size_image.getHeight()
                                + "x" + this.size_image.getWidth());

                        this.image_reader = ImageReader.newInstance(
                                this.size_image.getWidth(), this.size_image.getHeight(),
                                ImageFormat.YUV_420_888,
                                1);
                        this.image_reader.setOnImageAvailableListener(this.image_reader_listener,
                                background_handler);

                        this.filter.prepareForYUV(this.size_image);
                        this.current_image = Bitmap.createBitmap(this.size_image.getHeight(),
                                this.size_image.getWidth(), Bitmap.Config.ARGB_8888);

                        this.setPreviewTransform(width, height);
                    }
                }
            }
        }
        catch(CameraAccessException e) {
        }
    }

    private Size getPreferedPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> sizes = new ArrayList<>();
        Size result;
        for(Size option : mapSizes) {
            if(option.getWidth() > width && option.getHeight() > height) {
                sizes.add(option);
            }
        }

        if(sizes.size() > 0) {
            result = Collections.min(sizes, new Comparator<Size>() {
                        @Override
                        public int compare(Size a, Size b) {
                            return Long.signum(a.getWidth() * a.getHeight() - b.getWidth() * b.getHeight());
                        }
                    }
            );
        }
        else {
            result = mapSizes[0];
        }

        return result;
    }

    private void openCamera() {
        CameraManager camera_manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!this.camera_lock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            camera_manager.openCamera(this.camera_id_default, this.camera_dev_callback,
                    this.background_handler);
        } catch (CameraAccessException e) {

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try{
            this.camera_lock.acquire();
            if(this.camera_capture_session != null) {
                this.camera_capture_session.close();
                this.camera_capture_session = null;
            }
            if(this.camera_dev != null) {
                this.camera_dev.close();
                this.camera_dev = null;
            }
            if(this.image_reader != null) {
                this.image_reader.close();
                this.image_reader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            this.camera_lock.release();
        }
    }

    private ImageReader image_reader;
    private final ImageReader.OnImageAvailableListener image_reader_listener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    background_handler.post(new ImageSaver(reader.acquireLatestImage()));
                }
            };
    private class ImageSaver implements Runnable {
        private final Image image;
        private ImageSaver(Image image_local) {
            image = image_local;
        }
        /*private int convertYUVtoRGB(int y, int u, int v) {
            int r,g,b;

            r = y + (int)1.402f*v;
            g = y - (int)(0.344f*u +0.714f*v);
            b = y + (int)1.772f*u;
            r = r>255? 255 : r<0 ? 0 : r;
            g = g>255? 255 : g<0 ? 0 : g;
            b = b>255? 255 : b<0 ? 0 : b;
            return 0xFF000000 | (r<<16) | (g<<8) | (b<<0);
        }*/
        @Override
        public void run() {
            ByteBuffer buffer_y = image.getPlanes()[0].getBuffer();
            ByteBuffer buffer_u = image.getPlanes()[1].getBuffer();
            ByteBuffer buffer_v = image.getPlanes()[2].getBuffer();
            int len_y = buffer_y.remaining(), len_u = buffer_u.remaining(), len_v = buffer_v.remaining();
            byte[] bytes = new byte[len_y + len_u + len_v];
            buffer_y.get(bytes, 0, len_y);
            buffer_u.get(bytes, len_y, len_u);
            buffer_v.get(bytes, len_y + len_u, len_v);

            filter.setDataFromYUV(bytes);
            filter.doBatch();
            filter.waitTillBatchEnd();
            filter.copyCurrent(current_image);

            float average_time = 0.0f;
            long current_time_millisec = System.currentTimeMillis();
            time_intervals[time_interval_count] = (float)(current_time_millisec - last_time_millisec) / 1000.0f;
            last_time_millisec = current_time_millisec;
            time_interval_count--;
            if(time_interval_count < 0) {
                for(int i = 0; i < 5; i++) {
                    average_time += time_intervals[i];
                }
                average_time /= 5.0f;
                time_interval_count = 4;
            }
            final float average_time_copy = average_time;

            /*int width = size_image.getWidth();
            int height = size_image.getHeight();
            int size = width*height;
            int[] pixels = new int[size];
            int u, v, y1, y2, y3, y4;

            for(int i=0, k=0; i < size; i+=2, k+=1) {
                y1 = bytes[i  ]&0xff;
                y2 = bytes[i+1]&0xff;
                y3 = bytes[width+i  ]&0xff;
                y4 = bytes[width+i+1]&0xff;

                u = bytes[len_y+k+len_u]&0xff;
                v = bytes[len_y+k]&0xff;
                u = u-128;
                v = v-128;

                pixels[i  ] = convertYUVtoRGB(y1, u, v);
                pixels[i+1] = convertYUVtoRGB(y2, u, v);
                pixels[width+i  ] = convertYUVtoRGB(y3, u, v);
                pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

                if (i!=0 && (i+2)%width==0)
                    i+=width;
            }
            current_image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);*/
            ;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // real processing part
                    canvas.setImageBitmap(current_image);
                    if(average_time_copy > 0.0f) {
                        info_display.setText(String.format("%.2f", 1.0f / average_time_copy) + " FPS");
                    }
                }
            });
            image.close();
            if_camera_ready_for_next = true;
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private void selectImageResolution() {
        AlertDialog.Builder dialog_builder = new AlertDialog.Builder(this);;
        AlertDialog dialog;
        LinearLayout.LayoutParams layout_select_batch = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        ArrayAdapter<String> adapter_channels = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, this.size_image_list_str);
        final Spinner spinner_batches = new Spinner(this);
        spinner_batches.setAdapter(adapter_channels);
        spinner_batches.setLayoutParams(layout_select_batch);

        dialog_builder.setTitle("Select resolution");
        dialog_builder.setView(spinner_batches);
        dialog_builder.setPositiveButton("Try it", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if(size_image_list.length > 0) {
                    int id_resolution = (int) spinner_batches.getSelectedItemId();
                    size_image = size_image_list[id_resolution];
                    button_sel_resolution.setText(size_image.getHeight()
                            + "x" + size_image.getWidth());

                    filter.resetData();
                    filter.prepareForYUV(size_image);
                    current_image = Bitmap.createBitmap(size_image.getHeight(),
                            size_image.getWidth(), Bitmap.Config.ARGB_8888);

                    image_reader.close();
                    image_reader = ImageReader.newInstance(
                            size_image.getWidth(), size_image.getHeight(),
                            ImageFormat.YUV_420_888,
                            1);
                    image_reader.setOnImageAvailableListener(image_reader_listener,
                            background_handler);

                    camera_capture_session.close();
                    createCameraPreviewSession();

                    info_display.setText("Here displays Information like framerate");
                    last_time_millisec = System.currentTimeMillis();
                    time_interval_count = 4;
                }

            }
        });
        dialog_builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {}
        });

        dialog = dialog_builder.create();
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_stream);

        this.camera_preview = (TextureView)findViewById(R.id.textureview_livestream_preview);
        this.camera_id_default = "";
        this.if_camera_ready_for_next = true;

        this.button_load_batch = (Button)findViewById(R.id.button_livestream_loadbatch);
        this.button_back = (Button)findViewById(R.id.button_livestream_back);
        this.button_sel_resolution = (Button)findViewById(R.id.button_livestream_selresolution);
        this.button_load_batch.setOnClickListener(this);
        this.button_back.setOnClickListener(this);
        this.button_sel_resolution.setOnClickListener(this);

        this.canvas = (ImageView)findViewById(R.id.imageview_livestream_canvas);
        this.filter = new Filter(this);
        this.filter.resetData();

        this.info_display = (TextView)findViewById(R.id.textview_livestream_info);
        this.info_display.setText("Here displays Information like framerate");
        this.last_time_millisec = System.currentTimeMillis();
        this.time_intervals = new float[5];
        this.time_interval_count = 4;

        this.size_image_list_str = new String[0];
        this.size_image_list = new Size[0];

        this.debug = (TextView)findViewById(R.id.textView);
    }

    @Override
    public void onResume() {
        super.onResume();
        this.openBackgroundThread();
        if (this.camera_preview.isAvailable()) {
            this.setupCamera(this.camera_preview.getWidth(), this.camera_preview.getHeight());
            this.openCamera();
        }
        else {
            this.camera_preview.setSurfaceTextureListener(this.camera_preview_listener);
        }
    }

    @Override
    public void onPause() {
        this.closeCamera();
        this.closeBackgoundThread();
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        Intent in;
        switch (v.getId()) {
            case R.id.button_livestream_loadbatch:
                final String[] entries = new File(Environment.getExternalStorageDirectory()
                        + getString(R.string.script_dir)).list();
                AlertDialog.Builder dialog_builder = new AlertDialog.Builder(this);;
                AlertDialog dialog;
                LinearLayout.LayoutParams layout_select_batch = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

                ArrayAdapter<String> adapter_channels = new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item, entries);
                final Spinner spinner_batches = new Spinner(this);
                spinner_batches.setAdapter(adapter_channels);
                spinner_batches.setLayoutParams(layout_select_batch);

                dialog_builder.setTitle("Select file");
                dialog_builder.setView(spinner_batches);
                dialog_builder.setPositiveButton("Do it", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        int id_file = (int) spinner_batches.getSelectedItemId();
                        String str = entries[id_file];
                        File script_file = new File(Environment.getExternalStorageDirectory()
                                + getString(R.string.script_dir) + "/" + str);
                        filter.loadBatch(script_file);
                    }
                });
                dialog_builder.setNegativeButton("Do Nothing", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        filter.resetBatch();
                    }
                });

                dialog = dialog_builder.create();
                dialog.show();
                break;

            case R.id.button_livestream_back:
                in = new Intent(this, main.class);
                startActivity(in);
                break;

            case R.id.button_livestream_selresolution:
                this.selectImageResolution();
                break;
        }
    }
}
