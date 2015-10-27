// $Id: LOOPBACK.java,v 1.34 2010/01/15 12:23:57 belaban Exp $

package bboss.org.jgroups.protocols;


import bboss.org.jgroups.Event;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.PhysicalAddress;
import bboss.org.jgroups.stack.IpAddress;
import bboss.org.jgroups.util.Util;


/**
 Makes copies of outgoing messages, swaps sender and receiver and sends the message back up the stack.
 */
public class LOOPBACK extends TP {
    private String group_addr=null;
    private final PhysicalAddress physical_addr=new IpAddress(12345);

    public LOOPBACK() {
    }

    public boolean supportsMulticasting() {
        return false;
    }

    public String toString() {
        return "LOOPBACK(local address: " + local_addr + ')';
    }

    public void sendMulticast(byte[] data, int offset, int length) throws Exception {
    }

    public void sendUnicast(PhysicalAddress dest, byte[] data, int offset, int length) throws Exception {
    }

    public String getInfo() {
        return null;
    }

    protected PhysicalAddress getPhysicalAddress() {
        return physical_addr;
    }

    /*------------------------------ Protocol interface ------------------------------ */



    /**
     * Caller by the layer above this layer. Usually we just put this Message
     * into the send queue and let one or more worker threads handle it. A worker thread
     * then removes the Message from the send queue, performs a conversion and adds the
     * modified Message to the send queue of the layer below it, by calling Down).
     */
    public Object down(Event evt) {
        if(log.isTraceEnabled())
            log.trace("event is " + evt + ", group_addr=" + group_addr +
                      ", time is " + System.currentTimeMillis() + ", hdrs: " + Util.printEvent(evt));

        switch(evt.getType()) {

        case Event.MSG:
            Message msg=(Message)evt.getArg();
            Message rsp=msg.copy();
            if(rsp.getSrc() == null)
                rsp.setSrc(local_addr);

            //dest_addr=msg.getDest();
            //rsp.setDest(local_addr);
            //rsp.setSrc(dest_addr != null ? dest_addr : local_addr);
            up(new Event(Event.MSG, rsp));
            break;

        case Event.CONNECT:
        case Event.CONNECT_WITH_STATE_TRANSFER:    
        case Event.CONNECT_USE_FLUSH:
        case Event.CONNECT_WITH_STATE_TRANSFER_USE_FLUSH: 	
            group_addr=(String)evt.getArg();
            break;
        }
        return null;
    }



    /*--------------------------- End of Protocol interface -------------------------- */


}
