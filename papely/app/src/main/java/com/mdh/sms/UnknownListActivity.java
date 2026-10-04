package com.mdh.sms;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Unknown folder: silent messages from non-contacts (incl. marketing SMS). */
public class UnknownListActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText(renderUnknown());
        setContentView(tv);
    }

    private String renderUnknown() {
        List<String> rows = new ArrayList<>();
        Cursor c = null;
        try {
            c = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[] { "_id", "address", "body", "date" },
                    null, null, "date DESC LIMIT 200");
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    if (UnknownStore.folderOf(this, id)
                            == UnknownSenderFilter.Folder.UNKNOWN) {
                        String addr = c.getString(1);
                        String body = c.getString(2);
                        String preview = body == null ? "" :
                                (body.length() > 80 ? body.substring(0, 80) + "…" : body);
                        rows.add((addr == null ? "Unknown" : addr) + ": " + preview);
                    }
                }
            }
        } catch (SecurityException e) {
            return "Grant SMS permission.";
        } finally {
            if (c != null) c.close();
        }
        if (rows.isEmpty()) return "Unknown is empty. Marketing / non-contact SMS will appear here silently.";
        StringBuilder sb = new StringBuilder("Unknown (no notifications):\n\n");
        for (String r : rows) sb.append("• ").append(r).append("\n");
        return sb.toString();
    }
}
