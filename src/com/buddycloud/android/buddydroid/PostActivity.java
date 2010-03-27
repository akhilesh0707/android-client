package com.buddycloud.android.buddydroid;

import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smackx.pubsub.Item;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.PublishItem;
import org.jivesoftware.smackx.pubsub.packet.PubSub;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.buddycloud.android.buddydroid.provider.BuddyCloud.ChannelData;
import com.buddycloud.android.buddydroid.util.HumanTime;
import com.buddycloud.jbuddycloud.packet.BCAtom;

/**
 * Posting window, handles service interaction, content provider fetch of the
 * original posting and user interaction.
 */
public class PostActivity extends BCActivity implements OnClickListener {

    /**
     * The item to be replied to
     */
    private long itemId;

    /**
     * The text we will post
     */
    private String posting;

    /**
     * The channel node (e.g. /user/yourname@yourdomain.tld/channel)
     */
    private String node;

    /**
     * Create a new instance of the post window.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.post_message);

        int width = getWindowManager().getDefaultDisplay().getWidth();
        int height = getWindowManager().getDefaultDisplay().getHeight();

        if (width <= height) {
            height = (55 * height) / 100;
        } else {
            height = (90 * height) / 100;
            width = (80 * width) / 100;
        }
        getWindow().setLayout(width, height);

        setup(getIntent());
    }

    /**
     * Called on new intents, will reset the internal state.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        bindBCService();

        setup(intent);
    }

    /**
     * Initialize the internal state from an intent. Called by
     * <i>onNewIntent</i> and <i>onCreate</i>.
     * @param intent The intent with basic metadata.
     */
    private void setup(Intent intent) {

        // Fetch intent metadata
        long id = intent.getLongExtra("id", -1);
        String name = intent.getCharSequenceExtra("name").toString();
        node = intent.getCharSequenceExtra("node").toString();

        // fetch all views
        TextView titleView = (TextView) findViewById(R.id.title);
        TextView textView = (TextView) findViewById(R.id.message);
        TextView jidView = (TextView) findViewById(R.id.jid);
        TextView locationView = (TextView) findViewById(R.id.location);
        Button abortButton = (Button) findViewById(R.id.abort);
        Button postButton = (Button) findViewById(R.id.post);

        // listeners
        abortButton.setOnClickListener(this);
        postButton.setOnClickListener(this);

        if (id != -1) {

            // fetch parent data
            Cursor cursor = getContentResolver().query(
                ChannelData.CONTENT_URI,
                ChannelData.PROJECTION_MAP,
                ChannelData._ID + "=" + id,
                null,
                null
            );
            if (!cursor.moveToFirst()) {
                setResult(0);
                return;
            }

            final long parent =
                cursor.getLong(cursor.getColumnIndex(ChannelData.PARENT));

            if (parent != 0) {
                cursor.close();
                return;
            }

            itemId =
                cursor.getLong(cursor.getColumnIndex(ChannelData.ITEM_ID));
            final String originalText = cursor.getString(cursor.getColumnIndex(
                    ChannelData.CONTENT));
            final long timestamp = cursor.getLong(cursor.getColumnIndex(
                    ChannelData.ITEM_ID));
            final String town = cursor.getString(cursor.getColumnIndex(
                    ChannelData.GEOLOC_LOCALITY));
            final String country = cursor.getString(cursor.getColumnIndex(
                    ChannelData.GEOLOC_COUNTRY));
            final String jid =  cursor.getString(cursor.getColumnIndex(
                    ChannelData.AUTHOR_JID));
            final String affiliation = cursor.getString(cursor.getColumnIndex(
                    ChannelData.AUTHOR_AFFILIATION));

            cursor.close();

            // update UI

            titleView.setText("Reply");
            textView.setText(originalText);

            String humanTime = HumanTime.humanReadableString(
                    System.currentTimeMillis() - timestamp);

            jidView.setText(jid);

            if ("owner".equals(affiliation)) {
                jidView.setTextColor(Color.rgb(150, 15, 20));
            } else
            if ("moderator".equals(affiliation)) {
                jidView.setTextColor(Color.rgb(200, 130, 50));
            } else {
                jidView.setTextColor(Color.BLACK);
            }

            if (town != null && town.length() > 0) {
                if (country != null && country.length() > 0) {
                    locationView.setText(town + ", " + country + ", " +
                            humanTime);
                } else {
                    locationView.setText(town + ", " + humanTime);
                }
            } else {
                if (country != null && country.length() > 0) {
                    locationView.setText(country + ", " + humanTime);
                } else {
                    locationView.setText(humanTime);
                }
            }

        } else {
            itemId = 0l;
            titleView.setText("Post to " + name);
            textView.setVisibility(View.GONE);
        }

    }

    /**
     * Handle Post/Abort onClick.
     */
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.abort:
            setResult(0);
            finish();
            break;
        case R.id.post:
            posting = ((EditText)findViewById(R.id.posting)).getText().toString();
            if (post()) {
                setResult(0);
                finish();
            }
            break;
        }
    }

    /**
     * Post a new message to the current node.
     * @return true on success.
     */
    private boolean post() {
        BCAtom atom = new BCAtom();

        atom.setContent(posting);

        String fulljid = null;
        String jid = null;
        try {
            fulljid = jid = service.getJidWithResource();
            if (jid != null) {
                int pos = jid.indexOf('/');
                if (pos > 0) {
                    jid = jid.substring(0, pos);
                }
                atom.setAuthorJid(jid);
            }
        } catch (RemoteException e) {
            e.printStackTrace(System.err);
            unbindBCService();
            bindBCService();
        } catch (IllegalStateException e) {
            e.printStackTrace(System.err);
            unbindBCService();
            bindBCService();
        }

        if (itemId != 0) {
            atom.setParentId(itemId);
        }

        PayloadItem<BCAtom> item =
            new PayloadItem<BCAtom>(null, atom);
        PublishItem<Item> publish =
            new PublishItem<Item>(node, item);

        PubSub pubSub = new PubSub();
        pubSub.setFrom(fulljid);
        pubSub.setTo("broadcaster.buddycloud.com");
        pubSub.setType(Type.SET);

        pubSub.addExtension(publish);

        try {
            Log.d("POST", pubSub.toXML());
            service.send(pubSub.toXML());
            return true;
        } catch (RemoteException e) {
            e.printStackTrace(System.err);
            unbindBCService();
            bindBCService();
        } catch (IllegalStateException e) {
            e.printStackTrace(System.err);
            unbindBCService();
            bindBCService();
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            Log.d("REPOST", pubSub.toXML());
            service.send(pubSub.toXML());
            return true;
        } catch (RemoteException e) {
            e.printStackTrace(System.err);
            unbindBCService();
            bindBCService();
        } catch (IllegalStateException e) {
            e.printStackTrace(System.err);
            unbindBCService();
            bindBCService();
        }
        return false;
    }

}
