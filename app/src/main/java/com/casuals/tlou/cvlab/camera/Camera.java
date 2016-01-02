package com.casuals.tlou.cvlab.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import com.casuals.tlou.cvlab.R;
import com.casuals.tlou.cvlab.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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

public class Camera extends Activity implements View.OnClickListener {

    private Button button_camera_capture;
    private Button button_camera_back;
    //private CheckBox check_camera_noflash;

    // TextureView for preview
    private Size size_preview;
    private TextureView camera_preview;

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
    private String camera_id_front;
    private CameraDevice camera_dev;
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

    // capture request
    private CaptureRequest preview_capture_request;
    private CaptureRequest.Builder preview_capture_request_builder;
    private CameraCaptureSession camera_capture_session;
    private CameraCaptureSession.CaptureCallback camera_capture_session_callback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch(camera_state) {
                case STATE_PREVIEW:
                    // Do nothing
                    break;
                case STATE_WAIT_LOCK:
                    Integer af_state = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (af_state == null) {
                        captureStillImage();
                        camera_state = STATE_PICTURE_TAKEN;
                    }
                    else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == af_state ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == af_state) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (ae_state == null ||
                                ae_state == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            captureStillImage();
                            camera_state = STATE_PICTURE_TAKEN;
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae_state == null ||
                            ae_state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        //|| (ae_state == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED && !check_camera_noflash.isChecked()))
                        camera_state = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae_state == null || ae_state != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        captureStillImage();
                        camera_state = STATE_PICTURE_TAKEN;
                    }
                    break;
                }
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
                                //if(!check_camera_noflash.isChecked()) {
                                //    preview_capture_request_builder.set(CaptureRequest.CONTROL_AE_MODE,
                                //            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                //}
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

    // background handler and its thread
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
        // might still be wrong
        Matrix tf = new Matrix();
        float scale_x = ((float)size_preview.getHeight()) / ((float)view_width),
                scale_y = ((float)size_preview.getWidth()) / ((float)view_height),
                scaler = (scale_x > scale_y)?scale_x:scale_y;

        scale_x /= scaler;
        scale_y /= scaler;

        tf.setScale(scale_x, scale_y);
        camera_preview.setTransform(tf);
    }

    // set up the parameter of camera
    private void setupCamera(int width, int height) {
        CameraManager camera_manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        try {
            StreamConfigurationMap config;
            for(String str : camera_manager.getCameraIdList()) {
                CameraCharacteristics chars = camera_manager.getCameraCharacteristics(str);
                if(chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    if(this.camera_id_front.length() == 0) {
                        this.camera_id_front = str;
                    }
                }
                else {
                    if(this.camera_id_default.length() == 0) {
                        this.camera_id_default = str;
                        config = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                        Size largestImageSize = Collections.max(
                                Arrays.asList(config.getOutputSizes(ImageFormat.JPEG)),
                                new Comparator<Size>() {
                                    @Override
                                    public int compare(Size lhs, Size rhs) {
                                        return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                                rhs.getWidth() * rhs.getHeight());
                                    }
                                }
                        );
                        this.image_reader = ImageReader.newInstance(largestImageSize.getWidth(),
                                largestImageSize.getHeight(),
                                ImageFormat.JPEG,
                                3);
                        this.image_reader.setOnImageAvailableListener(this.image_reader_listener,
                                background_handler);

                        this.size_preview = this.getPreferedPreviewSize(
                                config.getOutputSizes(SurfaceTexture.class), width, height);
                        this.setPreviewTransform(width, height);
                    }

                }
            }
        }
        catch(CameraAccessException e) {
        }
    }

    // get best size for TextureView(this.camera_preview)
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

    // open camera
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

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private static final int STATE_PICTURE_TAKEN = 2;
    private static final int STATE_WAITING_PRECAPTURE = 3;
    private static final int STATE_WAITING_NON_PRECAPTURE = 4;
    private int camera_state;

    private void lockFocus() {
        try {
            this.camera_state = STATE_WAIT_LOCK;
            this.preview_capture_request_builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            this.camera_capture_session.capture(this.preview_capture_request_builder.build(),
                    this.camera_capture_session_callback, this.background_handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void unLockFocus() {
        try {
            this.camera_state = STATE_PREVIEW;
            this.preview_capture_request_builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            //if (!check_camera_noflash.isChecked()) {
            //    this.preview_capture_request_builder.set(CaptureRequest.CONTROL_AE_MODE,
            //            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            //}
            this.camera_capture_session.capture(this.preview_capture_request_builder.build(),
                    this.camera_capture_session_callback, this.background_handler);
            this.camera_capture_session.setRepeatingRequest(this.preview_capture_request, this.camera_capture_session_callback,
                    this.background_handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // safe image to file
    private File createImageFile() throws IOException {
        String name = "IMAGE";
        File dir = Environment.getExternalStoragePublicDirectory(getString(R.string.img_dir));
        File image = File.createTempFile(name, ".jpg", dir);
        return image;
    }

    public void takePhoto(View view) {
        try {
            this.image_file = createImageFile();
            this.lockFocus();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ImageReader image_reader;
    private File image_file;
    private final ImageReader.OnImageAvailableListener image_reader_listener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    background_handler.post(new ImageSaver(reader.acquireNextImage(), image_file));
                }
            };
    private static class ImageSaver implements Runnable {
        private final Image image;
        private final File file;
        private ImageSaver(Image image_local, File file_local) {
            image = image_local;
            file = file_local;
        }
        @Override
        public void run() {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream out_stream = null;
            try {
                out_stream = new FileOutputStream(file);
                out_stream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                file.delete();
            } finally {
                image.close();
                if(out_stream != null) {
                    try {
                        out_stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            this.preview_capture_request_builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            this.camera_state = STATE_WAITING_PRECAPTURE;
            this.camera_capture_session.capture(this.preview_capture_request_builder.build(),
                    this.camera_capture_session_callback, this.background_handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // real part of storing an image
    private void captureStillImage() {
        try {
            final CaptureRequest.Builder capture_builder =
                    this.camera_dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            capture_builder.addTarget(this.image_reader.getSurface());
            capture_builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //if(!check_camera_noflash.isChecked()) {
            //    capture_builder.set(CaptureRequest.CONTROL_AE_MODE,
            //           CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            //}

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

    // system calls of the class
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        this.button_camera_capture = (Button)findViewById(R.id.button_camera_capture);
        this.button_camera_back = (Button)findViewById(R.id.button_camera_back);
        //this.check_camera_noflash = (CheckBox)findViewById(R.id.checkBox_camera_noflash);

        this.button_camera_capture.setOnClickListener(this);
        this.button_camera_back.setOnClickListener(this);

        this.camera_preview = (TextureView)findViewById(R.id.camera_preview);
        this.camera_id_default = "";
        this.camera_id_front = "";
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
            case R.id.button_camera_capture:
                this.takePhoto(v);
                break;
            case R.id.button_camera_back:
                in = new Intent(this, main.class);
                startActivity(in);
                break;
        }
    }
}
