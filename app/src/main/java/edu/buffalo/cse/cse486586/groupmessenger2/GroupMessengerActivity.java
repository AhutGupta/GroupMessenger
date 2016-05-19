package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;


/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    static final String[] ports = {"11108", "11112", "11116", "11120", "11124" };
    static int count = 0;
    static int maxcount = 0;
    static int msgcount = 1;
    static int file_count = 0;
    static boolean tflag = true;
    static int myPort;
    static boolean failure = false;
    static boolean Qflag = true;
    static int failed_avd = 5;
    static final String New_Msg = "First_Contact";
    static final String Proposed_Seq = "Proposed_Sequence_Number";
    static final String Agreed_Seq = "Agreed_Sequence_Number";
    static final String Fail = "FAILURE";
    Hashtable<Integer, Proposed> table = new Hashtable();
    private Uri providerUri;

    public int PortMapping(int port){
        switch (port){
            case 11108:
                return 0;
            case 11112:
                return 1;
            case 11116:
                return 2;
            case 11120:
                return 3;
            case 11124:
                return 4;
        }
        return 5;
    }

    public int maxseq(int a, int b){
        if (a>b)
            return a+1;
        else
            return b+1;
    }

    public void AnnounceFail(int a){
        Message fail_msg = new Message();
        fail_msg.type = Fail;
        fail_msg.fail_avd = a;

        for (int i = 0; i < 5; i++) {

            try {
                if(i==a){
                    continue;
                }
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(ports[i]));
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                output.writeObject(fail_msg);
                output.flush();
                output.close();
                socket.close();

            } catch (UnknownHostException e) {
                Log.e(TAG, "Fail Broadcast UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "Fail Broadcast socket IOException");
            } catch (Exception e) {
                Log.e(TAG, "Fail Broadcast Exception");
            }

        }
    }


    public class Proposed{
        public int count=0;
        public float seq;
        public int avdlist[] = {0,0,0,0,0};
    }


    public PriorityQueue <Message> MsgQueue = new PriorityQueue();

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = (Integer.parseInt(portStr) * 2);
        msgcount = msgcount + PortMapping(myPort)*100;
        providerUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final EditText editText = (EditText) findViewById(R.id.editText1);
                String msg = editText.getText().toString() + "\n";
                editText.setText("");
                final TextView localtv = (TextView) findViewById(R.id.textView1);
                localtv.append("\t" + msg + "\n");
                Message msgObject = new Message();
                msgObject.type = New_Msg;
                msgObject.senderId = PortMapping(myPort);
                msgObject.uniqueId = msgcount++;
                msgObject.text = msg;
                Log.e(TAG, "I am the sender, my new message unique id : "+msgObject.uniqueId +", text: "+msgObject.text);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObject);
                return;
            }
        });
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            TelephonyManager tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
            final int myPort = (Integer.parseInt(portStr) * 2);
            int port = PortMapping(myPort);
            Socket socket;
            while (true) {
                try {
                    socket = serverSocket.accept();
                    BufferedInputStream bin = new BufferedInputStream(socket.getInputStream());
                    ObjectInputStream input = new ObjectInputStream(bin);
                    Message msg = (Message)input.readObject();
                    input.close();
                    bin.close();
                    socket.close();

                    if(msg.type.equals(Fail)){
                        Log.e(TAG, "In ServerTask. I got a Failure broadcast.....");
                        failure = true;
                        failed_avd = msg.fail_avd;
                    }

                    if(msg.type.equals(New_Msg)){
                        Log.e(TAG, "In Server. new_msg");
                        //Publish progres- Add to priority Q with proposed seq no.
                        publishProgress(msg.type, msg.text, msg.senderId+"", msg.uniqueId+"");
                    }

                    else if(msg.type.equals(Proposed_Seq)){
                        Log.e(TAG, "In server. Proposed_seq");
                        //Publish progress. and add proposed seq to hashtable.
                        publishProgress(msg.type, msg.text, msg.proposedby+"", msg.uniqueId+"", msg.seqno+"", msg.senderId+"");
                    }

                    else if(msg.type.equals(Agreed_Seq)){
                        Log.e(TAG, "In server. Agreed Seq...");
                        publishProgress(msg.type, msg.uniqueId+"", msg.seqno+"", msg.maxagreed+"");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            if(failure) {
                Set msg_set = table.entrySet();
                Iterator Tableiterator = msg_set.iterator();
                while (Tableiterator.hasNext()) {
                    Map.Entry msg_proposals = (Map.Entry) Tableiterator.next();
                    Proposed Pmsg = (Proposed) msg_proposals.getValue();
                    if (Pmsg.count == 4 && Pmsg.avdlist[failed_avd] == 0) {
                        Message temp_msg = new Message();
                        temp_msg.uniqueId = (Integer) msg_proposals.getKey();
                        temp_msg.seqno = ((Proposed) msg_proposals.getValue()).seq;
                        temp_msg.type = Agreed_Seq;
                        temp_msg.maxagreed = maxseq(temp_msg.seqno.intValue(), maxcount) - 1;
                        Tableiterator.remove();
                        Log.e(TAG, "In ServerTask. I am sending agreed seq no: " + temp_msg.seqno + " for unique id: " + temp_msg.uniqueId);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, temp_msg);

                    }
                }
            }

            //Remove msgs in the Q, sent by failed AVD
            if(failure) {
                Iterator MsgQiterator = MsgQueue.iterator();
                while (MsgQiterator.hasNext()) {
                    Message message = (Message) MsgQiterator.next();
                    if (message.senderId == failed_avd) {
                        MsgQueue.remove((message));
                        Log.e(TAG, "Removed message from Msg Queue. For crashed AVD- " + message.senderId);
                    }
                }
            }

            String status = strings[0];


            if(status.equals(New_Msg)){
                //Create a new msg object, with proposed seq no. Send it to client and add to Q
                int proposed_seq = maxseq(count, maxcount);
                Message proposed = new Message();
                proposed.type = Proposed_Seq;
                proposed.text = strings[1];
                proposed.senderId = Integer.parseInt(strings[2]);
                proposed.uniqueId = Integer.parseInt((strings[3]));
                proposed.seqno = proposed_seq + PortMapping(myPort)*0.1f;

                if(proposed.senderId!=failed_avd){
                    MsgQueue.add(proposed);
                    count++;
                    Log.e(TAG, "Added Message to the Queue and proposed seq..."+proposed);
                }
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, proposed);
            }



            else if(status.equals(Proposed_Seq)){

                Log.e(TAG, "In onProgressUpdate. Proposed_Seq...");

                //Add to hashtale and check if we have 5(or 4) entries. Decide on agreed seq no. if we do
                int key = Integer.parseInt(strings[3]);
                Float seq = Float.parseFloat(strings[4]);
                int sender = Integer.parseInt(strings[2]);
                int original_sender = Integer.parseInt(strings[5]);
                int count=0;
                if(!table.containsKey(key)){
                    Proposed proposed_msg = new Proposed();
                    proposed_msg.count=1;
                    count = proposed_msg.count;
                    proposed_msg.seq = seq;
                    proposed_msg.avdlist[sender] = 1;
                    table.put(key, proposed_msg);
                    Log.e(TAG, "First Hashtable entry for UID: " + key + " Count: "+proposed_msg.count+"For sender: "+sender + " Failure: " + failure);

                }

                else{
                    Proposed proposed_seq = table.get(key);
                    if (proposed_seq.avdlist[sender]==0){
                        proposed_seq.avdlist[sender]=1;
                        proposed_seq.count+=1;
                        if(seq>proposed_seq.seq){
                            proposed_seq.seq = seq;
                        }
                        else{
                            seq = proposed_seq.seq;
                        }
                        table.put(key, proposed_seq);
                        Log.e(TAG, "Later Hashtable entry for UID: " + key + " Count: "+ proposed_seq.count + " Max Seq: " + seq+ "Faliure: "+failure);
                    }
                    count = proposed_seq.count;

                }
                Log.e(TAG, "Count after insert: "+ count+" For sender: "+sender+ "Faliure: "+failure);

                //Check if count is 5 or 4
                boolean is_it = false;
                Proposed proposed_seq = table.get(key);
                if((failure && count==4 && proposed_seq.avdlist[failed_avd] == 0) || count == 5){
                    is_it = true;
                }
                if(is_it){
                    Message finalmsg = new Message();
                    finalmsg.type = Agreed_Seq;
                    finalmsg.seqno = seq;
                    finalmsg.uniqueId = key;
                    finalmsg.maxagreed = maxseq(seq.intValue(), maxcount)-1;
                    table.remove(key);
                    Log.e(TAG, "In onProgressUpdate. I am sending agreed seq no: "+finalmsg.seqno+" for unique id: "+finalmsg.uniqueId);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, finalmsg);

                }
            }

            else if(status.equals(Agreed_Seq)){

                int uid = Integer.parseInt(strings[1]);
                float seq_final = Float.parseFloat(strings[2]);
                int maxrecvd = Integer.parseInt(strings[3]);


                if(maxcount<maxrecvd){
                    maxcount = maxrecvd;
                }

                Log.e(TAG, "In onProgressUpdate. Agreed_seq...unique id is :"+uid+" agreed seq is :"+seq_final+" & maxAgreed for grp is :"+maxcount);

                Iterator Qiterator = MsgQueue.iterator();
                while(Qiterator.hasNext()){
                    Message message = (Message) Qiterator.next();
                    if(message.uniqueId == uid){
                        Log.e(TAG, "Added msg back to Q with correct priority...");
                        MsgQueue.remove(message);
                        message.seqno = seq_final;
                        message.deliverable = true;
                        MsgQueue.add(message);
                        Log.e(TAG, "---------Updated msg in Q. "+message);
                        break;
                    }
                }
                Log.e(TAG, "++++++++++Length of Q at this point = "+MsgQueue.size());

                while(!MsgQueue.isEmpty()) {

                    Log.e(TAG, "&&&&&&&&&&&&&&&Now inside the while loop for Q. status= "+MsgQueue.peek().deliverable);
                    Log.e(TAG, "The message at head: "+MsgQueue.peek());

                    if (MsgQueue.peek().deliverable) {
                        Log.e(TAG, "*********Now I should write to the content provider************");
                        String final_msg = MsgQueue.poll().text;
                        TextView localtv = (TextView) findViewById(R.id.textView1);
                        localtv.append("\t" + final_msg + "\n");
                        ContentValues keyValueToInsert = new ContentValues();
                        String sequence = Integer.toString(file_count++);
                        keyValueToInsert.put("key", sequence);
                        keyValueToInsert.put("value", final_msg);

                        Uri newUri = getContentResolver().insert(
                                providerUri,
                                keyValueToInsert
                        );
                    } else {
                        break;
                    }
                }

            }
            //Add to Content provider finally
            return;
        }
    }

    private class ClientTask extends AsyncTask<Message, Void, Void> {

        @Override
        protected Void doInBackground(Message... msgs) {

            Message msg = msgs[0];
            if(msg.type.equals(New_Msg)) {
                Log.e(TAG, "In client task.... New_msg");

                for (int i = 0; i < 5; i++) {

                    try {

                        if(i==failed_avd){
                            continue;
                        }
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(ports[i]));
                        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                        output.writeObject(msg);
                        output.flush();
                        output.close();
                        socket.close();

                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException");
                        if(!failure){
                            AnnounceFail(i);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parent Exception");
                    }

                }
            }
            else if(msg.type.equals(Proposed_Seq) && (msg.senderId != failed_avd) ){
                Log.e(TAG, "In Client task.. Proposed_seq");

                msg.proposedby = PortMapping(myPort);
                try{
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(ports[msg.senderId]));
                    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                    output.writeObject(msg);
                    output.flush();
                    output.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    if(!failure){
                        AnnounceFail(msg.senderId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parent Exception");
                }
            }

            else if(msg.type.equals(Agreed_Seq)){
                Log.e(TAG, "In client task. Agreed_seq.........");
                for (int i = 0; i < 5; i++) {

                    try{

                        if(i==failed_avd){
                            continue;
                        }
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(ports[i]));
                        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                        output.writeObject(msg);
                        output.flush();
                        output.close();
                        socket.close();

                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException");
                        if(!failure){
                            AnnounceFail(i);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parent Exception");
                    }
                }
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}