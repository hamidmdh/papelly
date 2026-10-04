package com.mdh.sms;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal conversation list with Inbox / Unknown tabs.
 * Inbox  = Telephony SMS where UnknownStore.folder != UNKNOWN
 * Unknown = Telephony SMS where UnknownStore.folder == UNKNOWN (silent, no notification posted)
 *
 * Production port: bind upstream ConversationListFragment/Adapter to a
 * folder-filtered query instead of this demo cursor filter.
 */
public class ConversationListActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button inbox = new Button(this);
        inbox.setText("Inbox");
        Button unknown = new Button(this);
        unknown.setText("Unknown");
        tabs.addView(inbox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(unknown, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(tabs);

        final TextView list = new TextView(this);
        root.addView(list);
        setContentView(root);

        inbox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { list.setText(renderFolder(false)); }
        });
        unknown.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(ConversationListActivity.this,
                        UnknownListActivity.class));
            }
        });
        list.setText(renderFolder(false));
    }

    private String renderFolder(boolean showUnknown) {
        List<String> rows = new ArrayList<>();
        Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri,
                    new String[] { "_id", "address", "body", "date" },
                    null, null, "date DESC LIMIT 100");
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String addr = c.getString(1);
                    String body = c.getString(2);
                    boolean isUnknown =
                            UnknownStore.folderOf(this, id) == UnknownSenderFilter.Folder.UNKNOWN;
                    if (isUnknown == showUnknown) {
                        String preview = body == null ? "" :
                                (body.length() > 60 ? body.substring(0, 60) + "…" : body);
                        rows.add((addr == null ? "Unknown" : addr) + ": " + preview);
                    }
                }
            }
        } catch (SecurityException e) {
            return "Grant SMS + Contacts permission and set Papely as Default SMS app.";
        } finally {
            if (c != null) c.close();
        }
        if (rows.isEmpty()) return showUnknown ? "Unknown is empty." : "Inbox is empty.";
        StringBuilder sb = new StringBuilder();
        for (String r : rows) sb.append("• ").append(r).append("\n");
        return sb.toString();
    }
}
