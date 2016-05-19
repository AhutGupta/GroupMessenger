package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;

/**
 * Created by ahut on 3/16/16.
 */
public class Message implements Serializable, Comparable<Message> {
    String type;
    int senderId = 0;
    int proposedby = 0;
    int uniqueId;
    int maxagreed;
    Float seqno = 1.0f;
    String text;
    boolean deliverable = false;
    int fail_avd = 5;

    @Override
    public int compareTo(Message another) {

        if (this.seqno < another.seqno) {
            return -1;
        } else if (this.seqno > another.seqno) {
            return 1;
        } else
            return 0;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", senderId=" + senderId +
                ", uniqueId=" + uniqueId +
                ", maxagreed=" + maxagreed +
                ", seqno=" + seqno +
                ", text='" + text + '\'' +
                ", deliverable=" + deliverable +
                '}';
    }
}
