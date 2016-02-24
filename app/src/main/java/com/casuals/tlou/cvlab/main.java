package com.casuals.tlou.cvlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import com.casuals.tlou.cvlab.camera.Camera;
import com.casuals.tlou.cvlab.imgproc.Gallerie;
import com.casuals.tlou.cvlab.stream.LiveStream;

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

public class main extends Activity implements View.OnClickListener {

    private Button button_camera;
    private Button button_swissknife;
    private Button button_livestream;
    private Button button_about;

    private void prepDir() {
        File folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.home_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }

        folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.img_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }

        folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.cache_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }

        folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.script_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }

        folder = new File(Environment.getExternalStorageDirectory()
                + getString(R.string.workshop_dir));
        if(!folder.exists()) {
            folder.mkdir();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.prepDir();

        this.button_camera = (Button)findViewById(R.id.button_camera);
        this.button_swissknife = (Button)findViewById(R.id.button_swissknife);
        this.button_livestream = (Button)findViewById(R.id.button_livestream);
        this.button_about = (Button)findViewById(R.id.button_about);

        this.button_camera.setOnClickListener(this);
        this.button_swissknife.setOnClickListener(this);
        this.button_livestream.setOnClickListener(this);
        this.button_about.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent in;
        switch (v.getId()) {
            case R.id.button_camera:
                in = new Intent(this, Camera.class);
                startActivity(in);
                break;

            case R.id.button_swissknife:
                in = new Intent(this, Gallerie.class);
                startActivity(in);
                break;

            case R.id.button_livestream:
                in = new Intent(this, LiveStream.class);
                startActivity(in);
                break;

            case R.id.button_about:
                TextView message = new TextView(this);
                TextView homepage = new TextView(this);
                TextView contact = new TextView(this);
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                LinearLayout content_dialog = new LinearLayout(this);
                SpannableString str;

                content_dialog.setOrientation(LinearLayout.VERTICAL);
                message.setText("Unlike most image processing applications, this " +
                                "one aims at image enhancement for technical usage. " +
                                "It is usually unavoidable to study the algorithm or implementation " +
                                "from other works, so please contact me if your right is violated. " +
                                "Also feel free to join if you are interested. This application is " +
                                "open source and follows Apache-2.0 License.");
                content_dialog.addView(message);
                str = new SpannableString( "Mainpage https://github.com/t-lou/CVLab");
                Linkify.addLinks(str, Linkify.WEB_URLS);
                homepage.setText(str);
                homepage.setMovementMethod(LinkMovementMethod.getInstance());
                content_dialog.addView(homepage);
                str = new SpannableString( "Contact: Tongxi Lou tongxi.lou@tum.de");
                Linkify.addLinks(str, Linkify.EMAIL_ADDRESSES);
                contact.setText(str);
                contact.setMovementMethod(LinkMovementMethod.getInstance());
                content_dialog.addView(contact);

                alert.setView(content_dialog);
                alert.setCancelable(true);
                alert.show();
                break;
        }
    }
}
