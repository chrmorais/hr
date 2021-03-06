/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 *
 * Created on 27/2/15 5:53 PM
 */
package com.odoo.base.addons.mail.widget;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.odoo.R;
import com.odoo.base.addons.mail.MailMessage;
import com.odoo.base.addons.res.ResPartner;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.orm.OModel;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.utils.OControls;
import com.odoo.core.utils.OResource;
import com.odoo.core.utils.OStringColorUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import odoo.OArguments;

public class MailChatterCompose extends ActionBarActivity implements View.OnClickListener {
    public static final String TAG = MailChatterCompose.class.getSimpleName();
    private OModel mModel;
    private int server_id = -1;
    private int partner_id = -1;

    public enum MessageType {
        Message, InternalNote
    }

    private MessageType mType = MessageType.Message;
    private View parent;
    private MailMessage mailMessage;
    private EditText edtSubject, edtBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_mail_chatter_message_compose);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        getSupportActionBar().hide();
        Bundle extra = getIntent().getExtras();
        mType = MessageType.valueOf(extra.getString("type"));
        mModel = OModel.get(this, extra.getString("model"), null);
        mailMessage = new MailMessage(this, null);
        server_id = extra.getInt("server_id");
        if (mModel.getModelName().equals("res.partner")) {
            partner_id = server_id;
        } else {
            ODataRow row = mModel.browse(mModel.selectRowId(server_id));
            for (OColumn col : mModel.getColumns(false)) {
                if (col.getType().isAssignableFrom(ResPartner.class)) {
                    if (col.getRelationType() != null
                            && col.getRelationType() == OColumn.RelationType.ManyToOne) {
                        ODataRow partner = null;
                        if (!row.getString(col.getName()).equals("false")) {
                            partner = row.getM2ORecord(col.getName()).browse();
                        }
                        if (partner != null && partner.getInt("id") != 0) {
                            partner_id = partner.getInt("id");
                        }
                    }
                }
            }
        }
        findViewById(R.id.btnSend).setOnClickListener(this);
        findViewById(R.id.btnCancel).setOnClickListener(this);
        edtSubject = (EditText) findViewById(R.id.messageSubject);
        edtBody = (EditText) findViewById(R.id.messageBody);
        init();
    }

    private void init() {
        TextView recordName = (TextView) findViewById(R.id.recordName);
        parent = (View) recordName.getParent();
        ODataRow record = mModel.browse(mModel.selectRowId(server_id));
        String name = record.getString(mModel.getDefaultNameColumn());
        recordName.setBackgroundColor(OStringColorUtil.getStringColor(this, name));
        if (mType == MessageType.Message) {
            edtSubject.setText("Re: " + name);
            recordName.setText(String.format(OResource.string(this, R.string.message_to), name));
        } else {
            recordName.setText(R.string.add_internal_note);
            edtSubject.setVisibility(View.GONE);
            edtBody.setHint(R.string.internal_note_hint);
            OControls.setText(parent, R.id.btnSend, R.string.label_log_note);
        }
        edtBody.requestFocus();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSend:
                sendMessage();
                break;
            case R.id.btnCancel:
                finish();
                break;
        }
    }

    private void sendMessage() {
        edtSubject.setError(null);
        edtBody.setError(null);
        if (mType == MessageType.Message) {
            if (TextUtils.isEmpty(edtSubject.getText())) {
                edtSubject.setError("Subject required");
                edtSubject.requestFocus();
                return;
            }
        }
        if (TextUtils.isEmpty(edtBody.getText())) {
            edtBody.setError(((mType == MessageType.Message) ? "Message" : "Note") + " required");
            edtBody.requestFocus();
            return;
        }
        String subject = (mType == MessageType.Message) ?
                edtSubject.getText().toString() : "false";
        MessagePost messagePost = new MessagePost();
        messagePost.execute(subject, edtBody.getText().toString());
    }


    private class MessagePost extends AsyncTask<String, Void, Boolean> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MailChatterCompose.this);
            progressDialog.setTitle(R.string.title_working);
            progressDialog.setMessage(((mType == MessageType.Message) ? "Sending message" :
                    "Logging internal note") + "...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                String subject = params[0];
                String body = params[1];
                OArguments args = new OArguments();
                args.add(server_id);
                JSONObject data = new JSONObject();
                data.put("body", body);
                data.put("subject", (subject.equals("false")) ? false : subject);
                data.put("parent_id", false);
                data.put("attachment_ids", new JSONArray());
                JSONArray partner_ids = new JSONArray();
                if (partner_id != -1 && mType == MessageType.Message) {
                    partner_ids.put(partner_id);
                }
                data.put("partner_ids", partner_ids);
                JSONObject context = new JSONObject();
                context.put("mail_read_set_read", true);
                context.put("default_res_id", server_id);
                context.put("default_model", mModel.getModelName());
                context.put("mail_post_autofollow", true);
                context.put("mail_post_autofollow_partner_ids", new JSONArray());
                data.put("context", context);
                data.put("type", "comment");
                data.put("content_subtype", "plaintext");
                data.put("subtype", (mType == MessageType.Message) ? "mail.mt_comment" : false);
                int newId = (int)
                        mModel.getServerDataHelper().callMethod("message_post", args, null, data);
                Thread.sleep(500);
                ODataRow row = new ODataRow();
                row.put("id", newId);
                mailMessage.quickCreateRecord(row);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            progressDialog.dismiss();
            Context ctx = MailChatterCompose.this;
            Intent intent = new Intent();
            intent.setAction("mail.message.update");
            ctx.sendBroadcast(intent);
            finish();

        }
    }
}
