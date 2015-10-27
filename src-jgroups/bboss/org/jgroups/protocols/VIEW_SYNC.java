package bboss.org.jgroups.protocols;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Event;
import bboss.org.jgroups.Global;
import bboss.org.jgroups.Header;
import bboss.org.jgroups.MergeView;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.View;
import bboss.org.jgroups.ViewId;
import bboss.org.jgroups.annotations.GuardedBy;
import bboss.org.jgroups.annotations.MBean;
import bboss.org.jgroups.annotations.ManagedAttribute;
import bboss.org.jgroups.annotations.ManagedOperation;
import bboss.org.jgroups.annotations.Property;
import bboss.org.jgroups.conf.ClassConfigurator;
import bboss.org.jgroups.protocols.pbcast.GMS;
import bboss.org.jgroups.stack.Protocol;
import bboss.org.jgroups.util.TimeScheduler;
import bboss.org.jgroups.util.Util;


/**
 * Periodically sends the view to the group. When a view is received which is
 * greater than the current view, we install it. Otherwise we simply discard it.
 * This is used to solve the problem for unreliable view dissemination outlined
 * in JGroups/doc/ReliableViewInstallation.txt. This protocol is supposed to be
 * just below GMS.
 * 
 * @author Bela Ban
 * @version $Id: VIEW_SYNC.java,v 1.39 2010/06/15 06:44:35 belaban Exp $
 */
@MBean(description="Periodically sends the view to the group")
public class VIEW_SYNC extends Protocol {
    
    /* -----------------------------------------    Properties     --------------------------------------- */

    @Property(description="View synchronization interval. Default is 60 sec")
    private long avg_send_interval=60000;

    /* --------------------------------------------- JMX  ---------------------------------------------- */

    private int num_views_sent=0;

    @ManagedAttribute
    private int num_view_requests_sent=0;

    @ManagedAttribute
    private int num_view_responses_seen=0;

    @ManagedAttribute
    private int num_views_non_local=0;

    @ManagedAttribute
    private int num_views_equal=0;

    @ManagedAttribute
    private int num_views_less=0;

    @ManagedAttribute
    private int num_views_adjusted=0;

    @ManagedAttribute
    private long last_view_request_sent=0;

    
    /* --------------------------------------------- Fields ------------------------------------------------------ */

    private Address local_addr=null;

    private final Vector<Address> mbrs=new Vector<Address>();

    private View my_view=null;

    private ViewId my_vid=null;

    @GuardedBy("view_task_lock")
    private Future<?> view_send_task_future=null; // bcasts periodic view sync message (added to timer below)

    private final Lock view_task_lock=new ReentrantLock();

    private TimeScheduler timer=null;

    private static final short gms_id=ClassConfigurator.getProtocolId(GMS.class);


    
    public long getAverageSendInterval() {
        return avg_send_interval;
    }

    public void setAverageSendInterval(long gossip_interval) {
        avg_send_interval=gossip_interval;
    }

    @ManagedAttribute
    public int getNumViewsSent() {
        return num_views_sent;
    }

    public int getNumViewsAdjusted() {
        return num_views_adjusted;
    }

    @ManagedOperation
    public void resetStats() {
        super.resetStats();
        num_views_adjusted=num_views_sent=0;
    }  

    public void start() throws Exception {
        timer=getTransport().getTimer();
        if(timer == null)
            throw new Exception("timer cannot be retrieved from protocol stack");
    }

    public void stop() {
        stopViewSender();
    }

    /** Sends a VIEW_SYNC_REQ to all members, every member replies with a VIEW multicast */
    @ManagedOperation(description="Sends a VIEW_SYNC_REQ to all members")
    public void sendViewRequest() {
        Message msg=new Message(null);
        msg.setFlag(Message.OOB);
        ViewSyncHeader hdr=new ViewSyncHeader(ViewSyncHeader.VIEW_SYNC_REQ, null);
        msg.putHeader(this.id, hdr);
        down_prot.down(new Event(Event.MSG, msg));
        num_view_requests_sent++;
        last_view_request_sent=System.currentTimeMillis();
    }

//    public void sendFakeViewForTestingOnly() {
//        ViewId fake_vid=new ViewId(local_addr, my_vid.getId() +2);
//        View fake_view=new View(fake_vid, new Vector(my_view.getMembers()));
//        System.out.println("sending fake view " + fake_view);
//        my_view=fake_view;
//        my_vid=fake_vid;
//        sendView();
//    }


    public Object up(Event evt) {
        Message msg;
        ViewSyncHeader hdr;
        int type=evt.getType();

        switch(type) {

            case Event.MSG:
                msg=(Message)evt.getArg();
                hdr=(ViewSyncHeader)msg.getHeader(this.id);
                if(hdr == null)
                    break;
                Address sender=msg.getSrc();
                switch(hdr.type) {
                    case ViewSyncHeader.VIEW_SYNC:
                        handleView(hdr.view, sender);
                        break;
                    case ViewSyncHeader.VIEW_SYNC_REQ:
                        if(!sender.equals(local_addr))
                            sendView();
                        break;
                    default:
                        if(log.isErrorEnabled()) log.error("ViewSyncHeader type " + hdr.type + " not known");
                }
                return null;

            case Event.VIEW_CHANGE:
                View view=(View)evt.getArg();
                handleViewChange(view);
                break;
        }

        return up_prot.up(evt);
    }



    public Object down(Event evt) {
        switch(evt.getType()) {
            case Event.VIEW_CHANGE:
                View v=(View)evt.getArg();
                handleViewChange(v);
                break;

            case Event.SET_LOCAL_ADDRESS:
                local_addr=(Address)evt.getArg();
                break;
        }
        return down_prot.down(evt);
    }



    /* --------------------------------------- Private Methods ---------------------------------------- */

    private void handleView(View v, Address sender) {
    	num_view_responses_seen++;
        Vector<Address> members=v.getMembers();
        if(!members.contains(local_addr)) {
            if(log.isWarnEnabled())
                log.warn("discarding view as I (" + local_addr + ") am not member of view (" + v + ")");
            num_views_non_local++;
            return;
        }

        ViewId vid=v.getVid();
        int rc=vid.compareTo(my_vid);
        if(rc > 0) { // foreign view is greater than my own view; update my own view !
            if(log.isTraceEnabled())
                log.trace("view from " + sender + " (" + vid + ") is greater than my own view (" + my_vid + ");" +
                " will update my own view");

            Message view_change=new Message(local_addr, local_addr, null);
            bboss.org.jgroups.protocols.pbcast.GMS.GmsHeader hdr;
            hdr=new bboss.org.jgroups.protocols.pbcast.GMS.GmsHeader(bboss.org.jgroups.protocols.pbcast.GMS.GmsHeader.VIEW, v);
            view_change.putHeader(gms_id, hdr);
            up_prot.up(new Event(Event.MSG, view_change));
            num_views_adjusted++;
        } else if (rc == 0) {
        	if (log.isTraceEnabled())
        		log.trace("view from "  + sender + " (" + vid + ") is same as my own view; ignoring");
            num_views_equal++;
       } else {
    	   if (log.isTraceEnabled())
    		   log.trace("view from "  + sender + " (" + vid + ") is less than my own view (" + my_vid + "); ignoring");
    	   num_views_less++;
        }
    }

    private void handleViewChange(View view) {
        Vector<Address> tmp=view.getMembers();
        if(tmp != null) {
            mbrs.clear();
            mbrs.addAll(tmp);
        }
        my_view=(View)view.clone();
        my_vid=my_view.getVid();
        if(my_view.size() > 1) {
            startViewSender();
        }
        else {
            stopViewSender();
        }
    }

    private void sendView() {
        View tmp=(View)(my_view != null? my_view.clone() : null);
        if(tmp == null) return;
        Message msg=new Message(null); // send to the group
        msg.setFlag(Message.OOB);
        ViewSyncHeader hdr=new ViewSyncHeader(ViewSyncHeader.VIEW_SYNC, tmp);
        msg.putHeader(this.id, hdr);
        down_prot.down(new Event(Event.MSG, msg));
        num_views_sent++;
    }

    /** Starts with view_task_lock held, no need to acquire it again */
    void startViewSender() {                   
        try {
        	view_task_lock.lock();
        	if(view_send_task_future == null || view_send_task_future.isDone()) {
	            ViewSendTask view_send_task=new ViewSendTask();
	            view_send_task_future=timer.scheduleWithDynamicInterval(view_send_task);
	            if(log.isTraceEnabled())
	                log.trace("view send task started");
        	}
        }
        finally {
            view_task_lock.unlock();
        }
    }


    void stopViewSender() {        
        try {
        	view_task_lock.lock();
            if(view_send_task_future != null) {
                view_send_task_future.cancel(false);
                view_send_task_future=null;
                if(log.isTraceEnabled())
                    log.trace("view send task stopped");
            }
        }
        finally {
            view_task_lock.unlock();
        }
    }






    /* ------------------------------------End of Private Methods ------------------------------------- */







    public static class ViewSyncHeader extends Header {
        public static final int VIEW_SYNC     = 1; // contains a view
        public static final int VIEW_SYNC_REQ = 2; // request to all members to send their views

        int   type=0;
        View  view=null;

        public ViewSyncHeader() {
        }


        public ViewSyncHeader(int type, View view) {
            this.type=type;
            this.view=view;
        }

        public int getType() {
            return type;
        }

        public View getView() {
            return view;
        }

        static String type2String(int t) {
            switch(t) {
                case VIEW_SYNC:
                    return "VIEW_SYNC";
                case VIEW_SYNC_REQ:
                    return "VIEW_SYNC_REQ";
                default:
                    return "<unknown>";
            }
        }

        public String toString() {
            StringBuilder sb=new StringBuilder("[").append(type2String(type)).append("]");
            if(view != null)
                sb.append(", view= ").append(view);
            return sb.toString();
        }

        public int size() {
            int retval=Global.INT_SIZE + Global.BYTE_SIZE + Global.BYTE_SIZE; // type + view type + presence for digest
            if(view != null)
                retval+=view.serializedSize();
            return retval;
        }

        public void writeTo(DataOutputStream out) throws IOException {
            out.writeInt(type);
            // 0 == null, 1 == View, 2 == MergeView
            byte b=(byte)(view == null? 0 : (view instanceof MergeView? 2 : 1));
            out.writeByte(b);
            Util.writeStreamable(view, out);
        }

        public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
            type=in.readInt();
            byte b=in.readByte();
            Class<?> clazz=b == 2? MergeView.class : View.class;
            view=(View)Util.readStreamable(clazz, in);
        }


    }




    /**
     Periodically multicasts a View_SYNC message
     */
    private class ViewSendTask implements TimeScheduler.Task {

        public long nextInterval() {
            long interval=computeSleepTime();
            if(interval <= 0)
                return 10000;
            else
                return interval;
        }


        public void run() {
            sendView();
        }

        long computeSleepTime() {
            int num_mbrs=Math.max(mbrs.size(), 1);
            return getRandom((num_mbrs * avg_send_interval * 2));
        }

        long getRandom(long range) {
            return (long)((Math.random() * range) % range);
        }
    }



}
