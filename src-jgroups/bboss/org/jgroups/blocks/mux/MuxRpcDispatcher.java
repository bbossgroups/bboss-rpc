package bboss.org.jgroups.blocks.mux;

import java.util.Collection;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Channel;
import bboss.org.jgroups.MembershipListener;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.MessageListener;
import bboss.org.jgroups.UpHandler;
import bboss.org.jgroups.blocks.GroupRequest;
import bboss.org.jgroups.blocks.RequestCorrelator;
import bboss.org.jgroups.blocks.RequestHandler;
import bboss.org.jgroups.blocks.RequestOptions;
import bboss.org.jgroups.blocks.RpcDispatcher;
import bboss.org.jgroups.blocks.RspFilter;

/**
 * A multiplexed message dispatcher.
 * When used in conjunction with a MuxUpHandler, allows multiple dispatchers to use the same channel.
 * <br/>
 * Usage:<br/>
 * <code>
 * Channel c = new JChannel(...);<br/>
 * c.setUpHandler(new MuxUpHandler());<br/>
 * <br/>
 * RpcDispatcher d1 = new MuxRpcDispatcher((short) 1, c, ...);<br/>
 * RpcDispatcher d2 = new MuxRpcDispatcher((short) 2, c, ...);<br/>
 * <br/>
 * c.connect(...);<br/>
 * </code>
 * @author Bela Ban
 * @author Paul Ferraro
 * @version $Id: MuxRpcDispatcher.java,v 1.2 2010/06/09 02:50:38 bstansberry Exp $
 */
public class MuxRpcDispatcher extends RpcDispatcher {

    private final short scope_id;
    
    public MuxRpcDispatcher(short scopeId) {
        super();
        this.scope_id = scopeId;
    }

    public MuxRpcDispatcher(short scopeId, Channel channel, MessageListener messageListener, MembershipListener membershipListener, Object serverObject) {
        this(scopeId);
        
        setMessageListener(messageListener);
        setMembershipListener(membershipListener);
        setServerObject(serverObject);
        setChannel(channel);
        channel.addChannelListener(this);
        start();
    }

    private Muxer<UpHandler> getMuxer() {
        UpHandler handler = channel.getUpHandler();
        return ((handler != null) && (handler instanceof MuxUpHandler)) ? (MuxUpHandler) handler : null;
    }

    @Override
    protected RequestCorrelator createRequestCorrelator(Object transport, RequestHandler handler, Address localAddr) {
        // We can't set the scope of the request correlator here
        // since this method is called from start() triggered in the
        // MessageDispatcher constructor, when this.scope is not yet defined
        return new MuxRequestCorrelator(scope_id, transport, handler, localAddr);
    }

    @Override
    public void start() {
        super.start();
        Muxer<UpHandler> muxer = this.getMuxer();
        if (muxer != null) {
            muxer.add(scope_id, this.getProtocolAdapter());
        }
    }

    @Override
    public void stop() {
        Muxer<UpHandler> muxer = this.getMuxer();
        if (muxer != null) {
            muxer.remove(scope_id);
        }
        super.stop();
    }

    @Override
    protected GroupRequest cast(Collection<Address> dests, Message msg, RequestOptions options, boolean blockForResults) {
        RspFilter filter = options.getRspFilter();
        return super.cast(dests, msg, options.setRspFilter((filter != null) ? new NoMuxHandlerRspFilter(filter) : new NoMuxHandlerRspFilter()), blockForResults);
    }
}
