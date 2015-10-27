package bboss.org.jgroups.blocks.mux;

import java.util.Collection;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.blocks.RequestCorrelator;
import bboss.org.jgroups.blocks.RequestHandler;
import bboss.org.jgroups.blocks.RequestOptions;
import bboss.org.jgroups.blocks.RspCollector;
import bboss.org.jgroups.conf.ClassConfigurator;

/**
 * A request correlator that adds a mux header to incoming and outgoing messages.
 * @author Bela Ban
 * @author Paul Ferraro
 * @author Brian Stansberry
 * @version $Id: MuxRequestCorrelator.java,v 1.4 2010/06/06 14:44:14 bstansberry Exp $
 */
public class MuxRequestCorrelator extends RequestCorrelator {

    protected final static short MUX_ID = ClassConfigurator.getProtocolId(MuxRequestCorrelator.class);
    private final bboss.org.jgroups.Header header;
    
    public MuxRequestCorrelator(short id, Object transport, RequestHandler handler, Address localAddr) {
        super(ClassConfigurator.getProtocolId(RequestCorrelator.class), transport, handler, localAddr);
        this.header = new MuxHeader(id);
    }

    @Override
    public void sendRequest(long requestId, Collection<Address> dest_mbrs, Message msg, RspCollector coll, RequestOptions options) throws Exception {
        msg.putHeader(MUX_ID, header);
        super.sendRequest(requestId, dest_mbrs, msg, coll, options);
    }

    @Override
    public void sendUnicastRequest(long id, Address target, Message msg, RspCollector coll) throws Exception {
        msg.putHeader(MUX_ID, header);
        super.sendUnicastRequest(id, target, msg, coll);
    }

    @Override
    protected void prepareResponse(Message rsp) {
        rsp.putHeader(MUX_ID, header);
        super.prepareResponse(rsp);
    }
}
