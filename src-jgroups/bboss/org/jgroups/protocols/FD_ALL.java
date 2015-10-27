package bboss.org.jgroups.protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Event;
import bboss.org.jgroups.Global;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.View;
import bboss.org.jgroups.annotations.DeprecatedProperty;
import bboss.org.jgroups.annotations.GuardedBy;
import bboss.org.jgroups.annotations.MBean;
import bboss.org.jgroups.annotations.ManagedAttribute;
import bboss.org.jgroups.annotations.ManagedOperation;
import bboss.org.jgroups.annotations.Property;
import bboss.org.jgroups.stack.Protocol;
import bboss.org.jgroups.util.BoundedList;
import bboss.org.jgroups.util.TimeScheduler;
import bboss.org.jgroups.util.Util;

/**
 * Failure detection based on simple heartbeat protocol. Every member
 * periodically multicasts a heartbeat. Every member also maintains a table of
 * all members (minus itself). When data or a heartbeat from P are received, we
 * reset the timestamp for P to the current time. Periodically, we check for
 * expired members, and suspect those.
 * 
 * @author Bela Ban
 * @version $Id: FD_ALL.java,v 1.34 2010/06/15 06:44:35 belaban Exp $
 */
@MBean(description="Failure detection based on simple heartbeat protocol")
@DeprecatedProperty(names={"shun"})
public class FD_ALL extends Protocol {
    
    /* -----------------------------------------    Properties     -------------------------------------------------- */

    @Property(description="Interval in which a HEARTBEAT is sent to the cluster")
    long interval=3000;

    @Property(description="Timeout after which a node P is suspected if neither a heartbeat nor data were received from P")
    long timeout=5000;
    
    @Property(description="Treat messages received from members as heartbeats. Note that this means we're updating " +
            "a value in a hashmap every time a message is passing up the stack through FD_ALL, which is costly. Default is false")
    boolean msg_counts_as_heartbeat=false;
    /* ---------------------------------------------   JMX      ------------------------------------------------------ */

    @ManagedAttribute(description="Number of heartbeats sent")
    protected int num_heartbeats_sent;

    @ManagedAttribute(description="Number of heartbeats received")
    protected int num_heartbeats_received=0;

    @ManagedAttribute(description="Number of suspected events received")
    protected int num_suspect_events=0;

    
    /* --------------------------------------------- Fields ------------------------------------------------------ */

    
    /** Map of addresses and timestamps of last updates */
    private final Map<Address,Long> timestamps=new ConcurrentHashMap<Address,Long>();

    private Address local_addr=null;
    
    private final List<Address> members=Collections.synchronizedList(new ArrayList<Address>());

    private TimeScheduler timer=null;

    // task which multicasts HEARTBEAT message after 'interval' ms
    @GuardedBy("lock")
    private ScheduledFuture<?> heartbeat_sender_future=null;

    // task which checks for members exceeding timeout and suspects them
    @GuardedBy("lock")
    private ScheduledFuture<?> timeout_checker_future=null;    

    private final BoundedList<Address> suspect_history=new BoundedList<Address>(20);
    
    private final Lock lock=new ReentrantLock();




    public FD_ALL() {}
    
    
    @ManagedAttribute(description="Member address")    
    public String getLocalAddress() {return local_addr != null? local_addr.toString() : "null";}
    @ManagedAttribute(description="Lists members of a cluster")
    public String getMembers() {return members.toString();}
    public int getHeartbeatsSent() {return num_heartbeats_sent;}
    public int getHeartbeatsReceived() {return num_heartbeats_received;}
    public int getSuspectEventsSent() {return num_suspect_events;}
    public long getTimeout() {return timeout;}
    public void setTimeout(long timeout) {this.timeout=timeout;}
    public long getInterval() {return interval;}
    public void setInterval(long interval) {this.interval=interval;}
    @Deprecated
    public static boolean isShun() {return false;}
    @Deprecated
    public void setShun(boolean flag) {}
    
    @ManagedAttribute(description="Are heartbeat tasks running")
    public boolean isRunning() {
        lock.lock();
        try{
            return isTimeoutCheckerRunning() && isHeartbeatSenderRunning();
        }
        finally{
            lock.unlock();
        }        
    }

    @ManagedOperation(description="Prints suspect history")
    public String printSuspectHistory() {
        StringBuilder sb=new StringBuilder();
        for(Address tmp: suspect_history) {
            sb.append(new Date()).append(": ").append(tmp).append("\n");
        }
        return sb.toString();
    }

    @ManagedOperation(description="Prints timestamps")
    public String printTimestamps() {
        return printTimeStamps();
    }
  

    public void resetStats() {
        num_heartbeats_sent=num_heartbeats_received=num_suspect_events=0;
        suspect_history.clear();
    }


    public void init() throws Exception {
        timer=getTransport().getTimer();
        if(timer == null)
            throw new Exception("timer not set");
    }


    public void stop() {
        stopHeartbeatSender();
        stopTimeoutChecker();
    }


    public Object up(Event evt) {
        Message msg;
        Header  hdr;

        switch(evt.getType()) {
            case Event.MSG:
                msg=(Message)evt.getArg();
                hdr=(Header)msg.getHeader(this.id);
                if(msg_counts_as_heartbeat)
                    update(msg.getSrc()); // update when data is received too ? maybe a bit costly
                if(hdr == null)
                    break;  // message did not originate from FD_ALL layer, just pass up

                switch(hdr.type) {
                    case Header.HEARTBEAT: 
                        Address sender=msg.getSrc();
                        if(sender.equals(local_addr))
                            break;
                        update(sender); // updates the heartbeat entry for 'sender'
                        num_heartbeats_received++;
                        break;          // don't pass up !

                    case Header.SUSPECT:
                        if(log.isTraceEnabled()) log.trace("[SUSPECT] suspect hdr is " + hdr);
                        down_prot.down(new Event(Event.SUSPECT, hdr.suspected_mbr));
                        up_prot.up(new Event(Event.SUSPECT, hdr.suspected_mbr));
                        break;
                }
                return null;            
        }
        return up_prot.up(evt); // pass up to the layer above us
    }


    public Object down(Event evt) {
        switch(evt.getType()) {
            case Event.VIEW_CHANGE:
                down_prot.down(evt);
                View v=(View)evt.getArg();
                handleViewChange(v);
                return null;
            case Event.SET_LOCAL_ADDRESS:
                local_addr=(Address)evt.getArg();
                break;
        }
        return down_prot.down(evt);
    }

    private void startTimeoutChecker() {
        lock.lock();
        try {
            if(!isTimeoutCheckerRunning()) {
                timeout_checker_future=timer.scheduleWithFixedDelay(new TimeoutChecker(), interval, interval, TimeUnit.MILLISECONDS);
            }
        }
        finally {
            lock.unlock();
        }
    }

    private void stopTimeoutChecker() {
         lock.lock();
         try {
             if(timeout_checker_future != null) {
                 timeout_checker_future.cancel(true);
                 timeout_checker_future=null;
             }
         }
         finally {
             lock.unlock();
         }
     }


    private void startHeartbeatSender() {
        lock.lock();
        try {
            if(!isHeartbeatSenderRunning()) {
                heartbeat_sender_future=timer.scheduleWithFixedDelay(new HeartbeatSender(), interval, interval, TimeUnit.MILLISECONDS);
            }
        }
        finally {
            lock.unlock();
        }
    }

     private void stopHeartbeatSender() {
        lock.lock();
        try {
            if(heartbeat_sender_future != null) {
                heartbeat_sender_future.cancel(true);
                heartbeat_sender_future=null;
            }
        }
        finally {
            lock.unlock();
        }
    }
     
    private boolean isTimeoutCheckerRunning() {
        return timeout_checker_future != null && !timeout_checker_future.isDone();
    }
     
    private boolean isHeartbeatSenderRunning() {
        return heartbeat_sender_future != null && !heartbeat_sender_future.isDone();
    }


    private void update(Address sender) {
        if(sender != null && !sender.equals(local_addr))
            timestamps.put(sender, System.currentTimeMillis());
    }


    private void handleViewChange(View v) {
        Vector<Address> mbrs=v.getMembers();
        boolean has_at_least_two=mbrs.size() > 1;

        members.clear();
        members.addAll(mbrs);

        Set<Address> keys=timestamps.keySet();
        keys.retainAll(mbrs); // remove all nodes which have left the cluster
        for(Address member:mbrs)
            update(member);

        if(has_at_least_two) {
            startHeartbeatSender();
            startTimeoutChecker();
        }
        else {
            stopHeartbeatSender();
            stopTimeoutChecker();
        }
    }



    private String printTimeStamps() {
        StringBuilder sb=new StringBuilder();
        
        long current_time=System.currentTimeMillis();
        for(Iterator<Entry<Address,Long>> it=timestamps.entrySet().iterator(); it.hasNext();) {
            Entry<Address,Long> entry=it.next();
            sb.append(entry.getKey()).append(": ");
            sb.append(current_time - entry.getValue().longValue()).append(" ms old\n");
        }
        return sb.toString();
    }

    void suspect(Address mbr) {
        Message suspect_msg=new Message();
        suspect_msg.setFlag(Message.OOB);
        Header hdr=new Header(Header.SUSPECT, mbr);
        suspect_msg.putHeader(this.id, hdr);
        down_prot.down(new Event(Event.MSG, suspect_msg));
        num_suspect_events++;
        suspect_history.add(mbr);
    }


    public static class Header extends bboss.org.jgroups.Header {
        public static final byte HEARTBEAT  = 0;
        public static final byte SUSPECT    = 1;

        byte    type=Header.HEARTBEAT;
        Address suspected_mbr=null;


        public Header() {
        }

        public Header(byte type) {
            this.type=type;
        }

        public Header(byte type, Address suspect) {
            this(type);
            this.suspected_mbr=suspect;
        }


        public String toString() {
            switch(type) {
                case FD_ALL.Header.HEARTBEAT:
                    return "heartbeat";
                case FD_ALL.Header.SUSPECT:
                    return "SUSPECT (suspected_mbr=" + suspected_mbr + ")";
                default:
                    return "unknown type (" + type + ")";
            }
        }


        public int size() {
            int retval=Global.BYTE_SIZE; // type
            retval+=Util.size(suspected_mbr);
            return retval;
        }

        public void writeTo(DataOutputStream out) throws IOException {
            out.writeByte(type);
            Util.writeAddress(suspected_mbr, out);
        }

        public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
            type=in.readByte();
            suspected_mbr=Util.readAddress(in);
        }

    }


    /**
     * Class which periodically multicasts a HEARTBEAT message to the cluster
     */
    class HeartbeatSender implements Runnable {

        public void run() {
            Message heartbeat=new Message(); // send to all
            heartbeat.setFlag(Message.OOB);
            Header hdr=new Header(Header.HEARTBEAT);
            heartbeat.putHeader(id, hdr);
            down_prot.down(new Event(Event.MSG, heartbeat));
            if(log.isTraceEnabled())
              log.trace(local_addr + " sent heartbeat to cluster");
            num_heartbeats_sent++;
        }
    }


    class TimeoutChecker implements Runnable {

        public void run() {                        
            
            if(log.isTraceEnabled())
                log.trace("checking for expired senders, table is:\n" + printTimeStamps());

            long current_time=System.currentTimeMillis(), diff;
            for(Iterator<Entry<Address,Long>> it=timestamps.entrySet().iterator(); it.hasNext();) {
                Entry<Address,Long> entry=it.next();
                Address key=entry.getKey();
                Long val=entry.getValue();
                if(val == null) {
                    it.remove();
                    continue;
                }
                diff=current_time - val.longValue();
                if(diff > timeout) {
                    if(log.isTraceEnabled())
                        log.trace("haven't received a heartbeat from " + key + " for " + diff + " ms, suspecting it");
                    suspect(key);
                }
            }
        }
    }
}
