
package bboss.org.jgroups.protocols;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Event;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.PhysicalAddress;
import bboss.org.jgroups.annotations.DeprecatedProperty;
import bboss.org.jgroups.annotations.ManagedOperation;
import bboss.org.jgroups.annotations.Property;
import bboss.org.jgroups.conf.PropertyConverters;
import bboss.org.jgroups.stack.RouterStub;
import bboss.org.jgroups.stack.RouterStubManager;
import bboss.org.jgroups.util.Promise;
import bboss.org.jgroups.util.Tuple;
import bboss.org.jgroups.util.UUID;


/**
 * The TCPGOSSIP protocol layer retrieves the initial membership (used by the
 * GMS when started by sending event FIND_INITIAL_MBRS down the stack). We do
 * this by contacting one or more GossipRouters, which must be running at
 * well-known addresses:ports. The responses should allow us to determine the
 * coordinator whom we have to contact, e.g. in case we want to join the group.
 * When we are a server (after having received the BECOME_SERVER event), we'll
 * respond to TCPGOSSIP requests with a TCPGOSSIP response.
 * <p>
 * The FIND_INITIAL_MBRS event will eventually be answered with a
 * FIND_INITIAL_MBRS_OK event up the stack.
 * 
 * @author Bela Ban
 * @version $Id: TCPGOSSIP.java,v 1.61 2010/06/16 17:43:36 vlada Exp $
 */
@DeprecatedProperty(names={"gossip_refresh_rate"})
public class TCPGOSSIP extends Discovery {
    
    /* -----------------------------------------    Properties     -------------------------------------------------- */
    
    @Property(description="Max time for socket creation. Default is 1000 msec")
    int sock_conn_timeout=1000;

    @Property(description="Max time in milliseconds to block on a read. 0 blocks forever")
    int sock_read_timeout=3000;

    @Property(description="Interval (ms) by which a disconnected stub attempts to reconnect to the GossipRouter")
    long reconnect_interval=10000L;
    
    @Property(name="initial_hosts", description="Comma delimited list of hosts to be contacted for initial membership", 
    		converter=PropertyConverters.InitialHosts2.class)
    public void setInitialHosts(List<InetSocketAddress> hosts) {
        if(hosts == null || hosts.isEmpty())
            throw new IllegalArgumentException("initial_hosts must contain the address of at least one GossipRouter");

        initial_hosts.addAll(hosts) ;
    }

    public List<InetSocketAddress> getInitialHosts() {
        return initial_hosts;
    }

    /* --------------------------------------------- Fields ------------------------------------------------------ */

    
    // (list of IpAddresses) hosts to be contacted for the initial membership
    private final List<InetSocketAddress> initial_hosts = new CopyOnWriteArrayList<InetSocketAddress>();
    
    private volatile RouterStubManager stubManager;

    public void init() throws Exception {
        super.init();
        stubManager = RouterStubManager.emptyGossipClientStubManager(this);
        if(timeout <= sock_conn_timeout)
            throw new IllegalArgumentException("timeout (" + timeout + ") must be greater than sock_conn_timeout ("
                    + sock_conn_timeout + ")");

        // we cannot use TCPGOSSIP together with TUNNEL (https://jira.jboss.org/jira/browse/JGRP-1101)
        TP transport=getTransport();
        if(transport instanceof TUNNEL)
            throw new IllegalStateException("TCPGOSSIP cannot be used with TUNNEL; use either TUNNEL:PING or " +
                    "TCP:TCPGOSSIP as valid configurations");
    }

    public void start() throws Exception {
        super.start();
    }

    public void stop() {
        super.stop();       
        stubManager.disconnectStubs();
    }

    public void destroy() {
        stubManager.destroyStubs();
        super.destroy();
    }

    public void handleConnect() {
        if (group_addr == null || local_addr == null) {
            if (log.isErrorEnabled())
                log.error("group_addr or local_addr is null, cannot register with GossipRouter(s)");
        } else {
            if (log.isTraceEnabled())
                log.trace("registering " + local_addr + " under " + group_addr + " with GossipRouter");
            
            stubManager.destroyStubs();
            stubManager = new RouterStubManager(this, group_addr, local_addr, reconnect_interval);
            for (InetSocketAddress host : initial_hosts) {
                stubManager.createAndRegisterStub(host.getHostName(), host.getPort(), null);                                
            }
            connectAllStubs(group_addr, local_addr);            
        }
    }


    public void handleDisconnect() {
        stubManager.disconnectStubs();
    }

    @SuppressWarnings("unchecked")
    public void sendGetMembersRequest(String cluster_name, Promise promise, boolean return_views_only) throws Exception {
        if (group_addr == null) {
            if (log.isErrorEnabled())
                log.error("cluster_name is null, cannot get membership");
            return;
        }

        if (log.isTraceEnabled())
            log.trace("fetching members from GossipRouter(s)");

        final List<PingData> responses = new LinkedList<PingData>();
        for (RouterStub stub : stubManager.getStubs()) {
            try {
                List<PingData> rsps = stub.getMembers(group_addr);
                responses.addAll(rsps);
            }
            catch(Throwable t) {
                log.warn("failed fetching members from " + stub.getGossipRouterAddress() + ": " +  t);
            }
        }

        final Set<Address> initial_mbrs = new HashSet<Address>();
        for (PingData rsp : responses) {
            Address logical_addr = rsp.getAddress();
            initial_mbrs.add(logical_addr);

            // 1. Set physical addresses
            Collection<PhysicalAddress> physical_addrs = rsp.getPhysicalAddrs();
            if (physical_addrs != null) {
                for (PhysicalAddress physical_addr : physical_addrs)
                    down(new Event(Event.SET_PHYSICAL_ADDRESS, new Tuple<Address, PhysicalAddress>(
                                    logical_addr, physical_addr)));
            }

            // 2. Set logical name
            String logical_name = rsp.getLogicalName();
            if (logical_name != null && logical_addr instanceof bboss.org.jgroups.util.UUID)
                bboss.org.jgroups.util.UUID.add((bboss.org.jgroups.util.UUID) logical_addr, logical_name);
        }

        if (initial_mbrs.isEmpty()) {
            if (log.isTraceEnabled())
                log.trace("[FIND_INITIAL_MBRS]: found no members");
            return;
        }
        if (log.isTraceEnabled())
            log.trace("consolidated mbrs from GossipRouter(s) are " + initial_mbrs);

        PhysicalAddress physical_addr=(PhysicalAddress)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
        PingData data=new PingData(local_addr, null, false, UUID.get(local_addr), Arrays.asList(physical_addr));

        for (Address mbr_addr : initial_mbrs) {
            Message msg = new Message(mbr_addr);
            msg.setFlag(Message.OOB);
            PingHeader hdr = new PingHeader(PingHeader.GET_MBRS_REQ, data, cluster_name);
            hdr.return_view_only = return_views_only;
            msg.putHeader(this.id, hdr);
            if (log.isTraceEnabled())
                log.trace("[FIND_INITIAL_MBRS] sending GET_MBRS_REQ request to " + mbr_addr);            
            down_prot.down(new Event(Event.MSG, msg));
        }
    }

    @ManagedOperation
    public void addInitialHost(String hostname, int port) {
        //if there is such a stub already, remove and destroy it
        removeInitialHost(hostname, port);
        
        //now re-add it
        InetSocketAddress isa = new InetSocketAddress(hostname, port);
        initial_hosts.add(isa);
        RouterStub s = new RouterStub(isa.getHostName(), isa.getPort(),null,stubManager);        
        connect(s, group_addr, local_addr);
        stubManager.registerStub(s);
    }

    @ManagedOperation
    public boolean removeInitialHost(String hostname, int port) {
        InetSocketAddress isa = new InetSocketAddress(hostname, port);        
        RouterStub unregisterStub = stubManager.unregisterStub(isa);
        if(unregisterStub != null) {
            stubManager.stopReconnecting(unregisterStub);
            unregisterStub.destroy();
        }
        return initial_hosts.remove(isa);        
    }


    protected void connectAllStubs(String group, Address logical_addr) {
        String logical_name=bboss.org.jgroups.util.UUID.get(logical_addr);
        PhysicalAddress physical_addr=(PhysicalAddress)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
        List<PhysicalAddress> physical_addrs=physical_addr != null? new ArrayList<PhysicalAddress>() : null;
        if(physical_addr != null)
            physical_addrs.add(physical_addr);

      
        for (RouterStub stub : stubManager.getStubs()) {
            try {
                if(log.isTraceEnabled())
                    log.trace("trying to connect to " + stub.getGossipRouterAddress());
                stub.connect(group, logical_addr, logical_name, physical_addrs);
            }
            catch(Exception e) {
                if(log.isErrorEnabled())
                    log.error("failed connecting to " + stub.getGossipRouterAddress() + ": " + e); 
                stubManager.startReconnecting(stub);
            }
        }     
    }
    
    protected void connect(RouterStub stub, String group, Address logical_addr) {
        String logical_name = bboss.org.jgroups.util.UUID.get(logical_addr);
        PhysicalAddress physical_addr = (PhysicalAddress) down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
        List<PhysicalAddress> physical_addrs = physical_addr != null ? new ArrayList<PhysicalAddress>(): null;
        if (physical_addr != null)
            physical_addrs.add(physical_addr);

        try {
            if (log.isTraceEnabled())
                log.trace("trying to connect to " + stub.getGossipRouterAddress());
            stub.connect(group, logical_addr, logical_name, physical_addrs);
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error("failed connecting to " + stub.getGossipRouterAddress() + ": " + e);
            stubManager.startReconnecting(stub);
        }
    }
}

