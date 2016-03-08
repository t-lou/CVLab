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

package com.casuals.tlou.cvlab.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.util.LinkedList;

public class BasicDialog {
    private class Item {
        private Object object;
        private char type;

        public Item(char type, Object object) {
            this.object = object;
            this.type = type;
        }

        public Object getObject() { return this.object; }
        public char getType() { return this.type; }
    }

    private Context context;
    private AlertDialog.Builder dialog_builder;
    private LinearLayout content_dialog;
    private LinearLayout.LayoutParams layout_params;
    private LinkedList<Item> input_items;

    public BasicDialog(Context context) {
        this.context = context;
        this.dialog_builder = new AlertDialog.Builder(context);
        this.input_items = new LinkedList<>();
        this.content_dialog = new LinearLayout(this.context);
        this.content_dialog.setOrientation(LinearLayout.VERTICAL);
        this.layout_params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        this.dialog_builder.setCancelable(true);
    }

    public BasicDialog(Context context, String message) {
        this(context);
        this.dialog_builder.setMessage(message);
        this.dialog_builder.show();
    }

    public void finish() {
        this.dialog_builder.setView(this.content_dialog);
        this.dialog_builder.show();
    }

    public AlertDialog.Builder getDialogBuilder() {
        return this.dialog_builder;
    }

    public void setTitle(String title) {
        if(this.dialog_builder != null) {
            this.dialog_builder.setTitle(title);
        }
    }

    public void addDropdownSelector(String[] items) {
        if(items.length < 1) return;
        if(this.context == null) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this.context,
                android.R.layout.simple_spinner_item, items);
        Spinner spinner = new Spinner(this.context);

        spinner.setAdapter(adapter);
        spinner.setLayoutParams(this.layout_params);
        this.content_dialog.addView(spinner);
        this.input_items.add(new Item('d', (Object)spinner));
    }

    public void addTextEditor(String hint) {
        if(this.context == null) return;

        EditText editor = new EditText(this.context);
        editor.setHint(hint);
        editor.setLayoutParams(this.layout_params);
        content_dialog.addView(editor);
        this.input_items.add(new Item('t', (Object)editor));
    }

    public Object getContentDirectly(int index) {
        if(index >= this.input_items.size()) return null;
        return this.input_items.get(index);
    }

    public Object getInputValue(int index) {
        if(index >= this.input_items.size()) return null;

        Item it = this.input_items.get(index);
        Object obj = null;
        switch (it.getType()) {
            // text input
            case 'd':
                obj = (Object)(Integer.valueOf(((Spinner)it.getObject()).getSelectedItemPosition()));
                break;
            // dropdown selector
            case 't':
                String text = ((EditText)it.getObject()).getText().toString();
                if(text.length() < 1) {
                    obj = null;
                }
                else {
                    try {
                        obj = (Object)(Float.valueOf(Float.parseFloat(text)));
                    } catch (NumberFormatException e) {
                        this.dialog_builder.setMessage("Input not recognised");
                        this.dialog_builder.show();
                        obj = null;
                    }
                }
                break;
            default:
        }
        return obj;
    }

    public int getNumItems() { return this.input_items.size(); }
}
