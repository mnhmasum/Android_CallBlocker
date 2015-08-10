package activity.masum.com.smsblock;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by masum on 30/07/2015.
 */
public class BlockingProcessReceiver extends BroadcastReceiver {
    Integer notificationId = 1207, requestId=1208;
    String msgBody;
    Context context;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        Bundle bundle = intent.getExtras();
        Object messages[] = (Object[]) bundle.get("pdus");
        final SmsMessage smsMessage[] = new SmsMessage[messages.length];

        for (int n = 0; n < messages.length; n++) {
            smsMessage[n] = SmsMessage.createFromPdu((byte[]) messages[n]);
        }

        msgBody = smsMessage[0].getDisplayMessageBody();

        String fromAddr = smsMessage[0].getOriginatingAddress();
        final Long threadId = getThreadIdFromAddress(context, (smsMessage[0].getOriginatingAddress()).toString());
        Log.d("SMSBlockingProcess", "1 onReceive");
        Log.d("SMSBlockingProcess", "Thread id " + threadId);
        Log.d("SMSBlockingProcess", "Original Address " + fromAddr);

        Toast.makeText(context, "T::: " + threadId + " ADD::: " + fromAddr, Toast.LENGTH_LONG).show();

        ifBlockedDeleteSMS(fromAddr, threadId, context, msgBody);
    }

    /*
	 *   If the "From Address" is Blacklisted,
	 *      then,  delete the SMS
	 *   else
	 *      raise a notification for the SMS
	 */

    protected void incommingBlockedSMS(Context context, String name, String number, String body) {
        if (name.length() == 0 || number.length() == 0) {
            Toast.makeText(context, "Please fill up both the fields", Toast.LENGTH_LONG);
            return;
        }

        SQLiteDatabase db;
        db = context.openOrCreateDatabase("/data/data/activity.masum.com.smsblock/databases/BlackListDB.db", SQLiteDatabase.CREATE_IF_NECESSARY, null);
        db.setVersion(1);
        db.setLocale(Locale.getDefault());
        db.setLockingEnabled(true);
        db.execSQL("create table IF NOT EXISTS sms_blocked(names varchar(20) UNIQUE, numbers varchar(20))");

        // Insert the "PhoneNumbers" into database-table, "SMS_BlackList"
        ContentValues values = new ContentValues();
        values.put("names", body);
        values.put("numbers", number);
        if (db.insert("sms_blocked", null, values) == -1){
            Log.d("addToSMS_BlackList", "3: blockingCodeForSMS ");
            Toast.makeText(context, name + " already exist in database\n Please try a new name!!", Toast.LENGTH_LONG).show();
            db.close();
            return;
        }

        Log.d("addToSMS_BlackList", "4: blockingCodeForSMS ");
        Log.d("addToSMS_BlackList", "5: blockingCodeForSMS ");

        db.close();
        Toast.makeText(context, name+" added to SMS blacklist", Toast.LENGTH_LONG).show();
        //finish();
    }

    private void ifBlockedDeleteSMS(final String fromAddr, final Long threadId, final Context context, String body) {
        // Creating a schedulable "delete SMS" task.
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                Looper.prepare();
                Log.i("Timer Task", "Delete START");
                deleteSMSInProgress(context, threadId);
                Log.i("Timer Task", "Delete END");
                Looper.loop();
            }
        };

        //Create a cursor for the "SMS_BlackList" table
        SQLiteDatabase db = SQLiteDatabase.openDatabase("/data/data/activity.masum.com.smsblock/databases/BlackListDB.db", null, SQLiteDatabase.OPEN_READWRITE);

        //Check, if the "fromAddr" exists in the BlackListDB
        Cursor c = db.query("SMS_BlackList", null, "numbers=?", new String[] { fromAddr }, null, null, null);
        Log.i("ifBlockedDeleteSMS", "c.moveToFirst(): " + c.moveToFirst() + "  c.getCount(): " + c.getCount());

        if (c.moveToFirst() && c.getCount() > 0) {
            // Scheduling the "delete SMS" task.
            new Timer().schedule(timerTask, 1500);

            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            manager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            incommingBlockedSMS(context, "Thread blocked " + threadId, fromAddr, body);

            c.close();
            db.close();
            return;
        }

        //Extract the name corresponding to the "Sender" from contacts
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(fromAddr));
        c = context.getContentResolver().query(lookupUri, null, null, null, null);
        if( ! c.moveToFirst()){
            Log.d("If","! c.moveToFirst(): "+ ! c.moveToFirst());
            db.close();
            c.close();
            //raiseNotification(context, fromAddr,threadId);
            return;
        }

        Log.d("ifBlockedDeleteSMS", c.moveToFirst()+"");
        Log.i("ifBlockedDeleteSMS","DisplayName: "+c.getString(c.getColumnIndex("display_name")));
        Log.i("ifBlockedDeleteSMS","Number: "+c.getString(c.getColumnIndex("number")));
        String name = c.getString(c.getColumnIndex("display_name"));
        Log.d("SMSBlockingProcess", "1 ifBlockedDeleteSMS");

        //Check, if the "Contact name" is present in BlackListDB
        db.execSQL("create table IF NOT EXISTS SMS_BlackList(names varchar(20) UNIQUE, numbers varchat(20))");
        c.close();
        c = db.query("SMS_BlackList", null, "names=?", new String[] { name },
                null, null, null);

        Log.d("SMSBlockingProcess", "2 ifBlockedDeleteSMS");
        Log.i("SMSBlockingProcess", "c.getCount: "+c.getCount());
        if (c.getCount() <= 0) {
            Log.d("BlockCallsPressed", "ifBlockedDeleteSMS");
            Log.d("If","c.getCount(): "+c.getCount());
            db.close();
            c.close();
            //raiseNotification(context, name,threadId);
            return;
        }
        db.close();
        c.close();


        // Scheduling the "delete SMS" task.
        new Timer().schedule(timerTask, 1500);
        Log.d("SMSBlockingProcess", " ifBlockedDeleteSMS Ended");
    }




    /*
     * Notify the user, about the SMS
     *//*
    private void raiseNotification(Context context, String from, Long threadId) {

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification mNotification = new Notification(R.drawable.flying_robo, "Message from: " + from, System.currentTimeMillis());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("content://mms-sms/conversations/" + threadId));

        PendingIntent mPendingIntent = PendingIntent.getActivity(context.getApplicationContext(), requestId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mNotification.setLatestEventInfo(context, from, msgBody, mPendingIntent);

        mNotification.defaults |= Notification.DEFAULT_SOUND;
        mNotification.defaults |= Notification.DEFAULT_VIBRATE;
        mNotification.flags |= Notification.FLAG_AUTO_CANCEL;

        mNotificationManager.notify(notificationId, mNotification);
    }


    *//*
     * 	 Deleting the Latest SMS present in InBox, using the Thread Id.
     */
    private static void deleteSMSInProgress(Context context, long thread_id) {
        Uri inbox = Uri.parse("content://sms/inbox");
        Cursor c = context.getContentResolver().query(inbox, null, null, null, "date desc");

        Log.i("Timer Task", (c==null) + "  " + c.moveToFirst());
        if (c == null || !c.moveToFirst()){
            c.close();
            return;
        }

        Log.i("Timer Task", "Delete IN PROGRESS");
        String from = c.getString(c.getColumnIndex("address"));
        c.close();


        Uri thread = Uri.parse("content://sms/conversations/" + thread_id);
        context.getContentResolver().delete(thread, null, null);
        Log.i("Timer Task", "Delete Successful");

        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

    }

    /*
     *   Retrieving the SMS Thread Id, using the Phone number
     */
    public static long getThreadIdFromAddress(Context context, String address) {
        if (address == null)
            return 0;

        String THREAD_RECIPIENT_QUERY = "recipient";

        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();
        uriBuilder.appendQueryParameter(THREAD_RECIPIENT_QUERY, address);

        long threadId = 0;

        Cursor cursor = context.getContentResolver().query(uriBuilder.build(), new String[] { "_id" }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    threadId = cursor.getLong(0);
                }
            }
            finally {
                cursor.close();
            }
        }
        return threadId;
    }

}
