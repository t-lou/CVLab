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
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
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

import java.io.ByteArrayOutputStream;
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

    private Size size_preview;
    private Size size_image;
    private TextureView camera_preview;
    private ImageView canvas;
    private Bitmap current_image;
    private Filter filter;
    private Button button_load_batch;
    private Button button_back;

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
    private int camera_upsample_level;
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

        private void process(CaptureResult result) {
            if(if_camera_ready_for_next) {
                lockFocus();
                captureStillImage();
            }
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                     long timestamp, long frame_number) {
            super.onCaptureStarted(session, request, timestamp, frame_number);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
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

            Surface previewSurface = new Surface(surface_texture);
            this.preview_capture_request_builder =
                    this.camera_dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            this.preview_capture_request_builder.addTarget(previewSurface);
            this.camera_dev.createCaptureSession(Arrays.asList(previewSurface, image_reader.getSurface()),
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

                        this.image_reader = ImageReader.newInstance(
                                this.size_image.getWidth(), this.size_image.getHeight(),
                                ImageFormat.YUV_420_888,
                                1);
                        this.image_reader.setOnImageAvailableListener(this.image_reader_listener,
                                background_handler);

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

    private void lockFocus() {
        try {
            this.preview_capture_request_builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            this.camera_capture_session.capture(this.preview_capture_request_builder.build(),
                    this.camera_capture_session_callback, this.background_handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        this.if_camera_ready_for_next = false;
    }

    private void unLockFocus() {
        try {
            this.preview_capture_request_builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            this.camera_capture_session.capture(this.preview_capture_request_builder.build(),
                    this.camera_capture_session_callback, this.background_handler);
            this.camera_capture_session.setRepeatingRequest(this.preview_capture_request, this.camera_capture_session_callback,
                    this.background_handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
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
        @Override
        public void run() {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            YuvImage yuvimage = new YuvImage(bytes, ImageFormat.NV21, size_image.getWidth(), size_image.getHeight(), null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, size_image.getWidth(), size_image.getHeight()), 80, baos);
            byte[] jdata = baos.toByteArray();
            BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
            bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

            current_image = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
            filter.setData(current_image);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // real processing part
                    //filter.doBatch();
                    //filter.waitTillBatchEnd();
                    //filter.copyCurrent(current_image);
                    canvas.setImageBitmap(current_image);
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

    private void captureStillImage() {
        try {
            final CaptureRequest.Builder capture_builder =
                    this.camera_dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            capture_builder.addTarget(this.image_reader.getSurface());
            capture_builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            capture_builder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    unLockFocus();
                }
            };

            this.camera_capture_session.stopRepeating();
            this.camera_capture_session.capture(capture_builder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
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
        this.button_load_batch.setOnClickListener(this);
        this.button_back.setOnClickListener(this);

        this.canvas = (ImageView)findViewById(R.id.imageview_livestream_canvas);
        this.camera_upsample_level = 3;
        this.filter = new Filter(this);

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
                        int if_file = (int) spinner_batches.getSelectedItemId();
                        String str = entries[if_file];
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
        }
    }
}
