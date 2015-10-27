// $Id: HDRS.java,v 1.9 2010/03/05 09:04:54 belaban Exp $

package bboss.org.jgroups.protocols;

import java.util.Map;

import bboss.org.jgroups.Event;
import bboss.org.jgroups.Header;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.annotations.Unsupported;
import bboss.org.jgroups.conf.ClassConfigurator;
import bboss.org.jgroups.stack.Protocol;


/**
 * Example of a protocol layer. Contains no real functionality, can be used as a template.
 */
@Unsupported
public class HDRS extends Protocol {

    private static void printMessage(Message msg, String label) {
        StringBuilder sb=new StringBuilder();
        sb.append(label).append(":\n");
        Map<Short, Header> hdrs=msg.getHeaders();
        sb.append(print(msg, hdrs));
        System.out.println(sb);
    }

    private static String print(Message msg, Map<Short, Header> hdrs) {
        StringBuilder sb=new StringBuilder();
        int hdrs_size=0;
        for(Map.Entry<Short,Header> entry: hdrs.entrySet()) {

            Class clazz=ClassConfigurator.getProtocol(entry.getKey());
            String name=clazz != null? clazz.getSimpleName() : null;
            Header hdr=entry.getValue();
            int size=hdr.size();
            hdrs_size+=size;
            sb.append(name).append(": ").append(" ").append(size).append(" bytes\n");
        }
        sb.append("headers=").append(hdrs_size).append(", total msg size=").append(msg.size());
        sb.append(", msg payload=").append(msg.getLength()).append("\n");
        return sb.toString();
    }




    public Object up(Event evt) {
        if(evt.getType() == Event.MSG) {
            Message msg=(Message)evt.getArg();
            printMessage(msg, "up");
        }
        return up_prot.up(evt); // Pass up to the layer above us
    }



    public Object down(Event evt) {
        if(evt.getType() == Event.MSG) {
            Message msg=(Message)evt.getArg();
            printMessage(msg, "down");
        }

        return down_prot.down(evt);  // Pass on to the layer below us
    }


}
