// $Id: HTOTAL.java,v 1.15 2010/06/15 06:44:35 belaban Exp $

package bboss.org.jgroups.protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Event;
import bboss.org.jgroups.Header;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.View;
import bboss.org.jgroups.annotations.Experimental;
import bboss.org.jgroups.annotations.Property;
import bboss.org.jgroups.stack.Protocol;
import bboss.org.jgroups.util.Util;


/**
 * Implementation of UTO-TCP as designed by EPFL. Implements chaining algorithm: each sender sends the message
 * to a coordinator who then forwards it to its neighbor on the right, who then forwards it to its neighbor to the right
 * etc.<p/>
 * This protocol has not yet been completed and is experimental at best !
 * @author Bela Ban
 * @version $Id: HTOTAL.java,v 1.15 2010/06/15 06:44:35 belaban Exp $
 */
@Experimental
public class HTOTAL extends Protocol {
    Address coord=null;
    Address neighbor=null; // to whom do we forward the message (member to the right, or null if we're at the tail)
    Address local_addr=null;
    Vector  mbrs=new Vector();
    boolean is_coord=false;
    @Property
    private boolean use_multipoint_forwarding=false;




    public HTOTAL() {
    }


    public Object down(Event evt) {
        switch(evt.getType()) {
            case Event.VIEW_CHANGE:
                determineCoordinatorAndNextMember((View)evt.getArg());
                break;
            case Event.SET_LOCAL_ADDRESS:
                local_addr=(Address)evt.getArg();
                break;
            case Event.MSG:
                Message msg=(Message)evt.getArg();
                Address dest=msg.getDest();
                if(dest == null || dest.isMulticastAddress()) { // only process multipoint messages
                    if(coord == null)
                        log.error("coordinator is null, cannot send message to coordinator");
                    else {
                        msg.setSrc(local_addr);
                        forwardTo(coord, msg);
                    }
                    return null; // handled here, don't pass down by default
                }
                break;
        }
        return down_prot.down(evt);
    }

    public Object up(Event evt) {
        switch(evt.getType()) {
            case Event.VIEW_CHANGE:
                determineCoordinatorAndNextMember((View)evt.getArg());
                break;
            case Event.MSG:
                Message msg=(Message)evt.getArg();
                HTotalHeader hdr=(HTotalHeader)msg.getHeader(this.id);

                if(hdr == null)
                    break;  // probably a unicast message, just pass it up

                Message copy=msg.copy(false); // do not copy the buffer
                if(use_multipoint_forwarding) {
                    copy.setDest(null);
                    down_prot.down(new Event(Event.MSG, copy));
                }
                else {
                    if(neighbor != null) {
                        forwardTo(neighbor, copy);
                    }
                }

                msg.setDest(hdr.dest); // set destination to be the original destination
                msg.setSrc(hdr.src);   // set sender to be the original sender (important for retransmission requests)

                return up_prot.up(evt); // <-- we modify msg directly inside evt
        }
        return up_prot.up(evt);
    }

    private void forwardTo(Address destination, Message msg) {
        HTotalHeader hdr=(HTotalHeader)msg.getHeader(this.id);

        if(hdr == null) {
            hdr=new HTotalHeader(msg.getDest(), msg.getSrc());
            msg.putHeader(this.id, hdr);
        }
        msg.setDest(destination);
        if(log.isTraceEnabled())
            log.trace("forwarding message to " + destination + ", hdr=" + hdr);
        down_prot.down(new Event(Event.MSG, msg));
    }


    private void determineCoordinatorAndNextMember(View v) {
        Object tmp;
        Address retval=null;

        mbrs.clear();
        mbrs.addAll(v.getMembers());

        coord=(Address)(mbrs != null && !mbrs.isEmpty()? mbrs.firstElement() : null);
        is_coord=coord != null && local_addr != null && coord.equals(local_addr);

        if(mbrs == null || mbrs.size() < 2 || local_addr == null)
            neighbor=null;
        else {
            for(int i=0; i < mbrs.size(); i++) {
                tmp=mbrs.elementAt(i);
                if(local_addr.equals(tmp)) {
                    if(i + 1 >= mbrs.size())
                        retval=null; // we don't wrap, last member is null
                    else
                        retval=(Address)mbrs.elementAt(i + 1);
                    break;
                }
            }
        }
        neighbor=retval;
        if(log.isTraceEnabled())
            log.trace("coord=" + coord + ", neighbor=" + neighbor);
    }


    public static class HTotalHeader extends Header {
        Address dest, src;

        public HTotalHeader() {
        }

        public HTotalHeader(Address dest, Address src) {
            this.dest=dest;
            this.src=src;
        }

        public void writeTo(DataOutputStream out) throws IOException {
            Util.writeAddress(dest, out);
            Util.writeAddress(src, out);
        }

        public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
            dest=Util.readAddress(in);
            src=Util.readAddress(in);
        }

        public int size() {
            return Util.size(dest) + Util.size(src);
        }

        public String toString() {
            return "dest=" + dest + ", src=" + src;
        }
    }

}
