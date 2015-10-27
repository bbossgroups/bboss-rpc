package bboss.org.jgroups.protocols;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;

import bboss.org.jgroups.Event;
import bboss.org.jgroups.Global;
import bboss.org.jgroups.annotations.Experimental;
import bboss.org.jgroups.annotations.Property;
import bboss.org.jgroups.conf.PropertyConverters;
import bboss.org.jgroups.stack.IpAddress;
import bboss.org.jgroups.util.Util;

/**
 * Protocol which uses InetAddress.isReachable() to check whether a given host
 * is up or not, taking 1 argument; the host name of the host to be pinged.
 * <em>Note that this protocol only works with JDK 5 !</em> The implementation
 * of this may or may not use ICMP ! An alternative is to create a TCP
 * connection to port 7 (echo service) and see whether it works ! This is
 * obviously done in JDK 5, so unless an echo service is configured to run, this
 * won't work...
 * 
 * @author Bela Ban
 * @version $Id: FD_ICMP.java,v 1.16 2010/06/10 07:02:25 belaban Exp $
 */
@Experimental
public class FD_ICMP extends FD {

    /** network interface to be used to send the ICMP packets */
    private NetworkInterface intf=null;

    @Property(name="bind_addr",
              description="The NIC on which the ServerSocket should listen on. " +
                      "The following special values are also recognized: GLOBAL, SITE_LOCAL, LINK_LOCAL and NON_LOOPBACK", 
              systemProperty={Global.BIND_ADDR, Global.BIND_ADDR_OLD},
              defaultValueIPv4=Global.NON_LOOPBACK_ADDRESS, defaultValueIPv6=Global.NON_LOOPBACK_ADDRESS)
    private InetAddress bind_addr=null ;
    
    @Property(name="bind_interface", converter=PropertyConverters.BindInterface.class,
    		description="The interface (NIC) which should be used by this transport", dependsUpon="bind_addr")
    protected String bind_interface_str=null;
     
    private Method is_reacheable;

    /** Time-to-live for InetAddress.isReachable() */
    @Property
    private int ttl=32;


    public void init() throws Exception {
        super.init();
        if(bind_addr != null)
            intf=NetworkInterface.getByInetAddress(bind_addr);

        try {
            Class<?> is_reacheable_class=Util.loadClass("java.net.InetAddress", this.getClass());
            is_reacheable=is_reacheable_class.getMethod("isReachable", NetworkInterface.class, int.class, int.class);
        }
        catch(ClassNotFoundException e) {
            // should never happen since we require JDK 1.5
            Error error=new NoClassDefFoundError("failed checking for InetAddress.isReachable() method - requires JDK 5 or higher");
            error.initCause(e);
            throw error;
        }
        catch(NoSuchMethodException e) {
            // log.error("didn't find InetAddress.isReachable() method - requires JDK 5 or higher");
            Error error=new NoSuchMethodError("didn't find InetAddress.isReachable() method - requires JDK 5 or higher");
            error.initCause(e);
            throw error;
        }
    }

    public Object up(Event evt) {
        switch(evt.getType()) {
            case Event.CONFIG:
                if(bind_addr == null) {
                    Map<String,Object> config=(Map<String,Object>)evt.getArg();
                    bind_addr=(InetAddress)config.get("bind_addr");
                }
                break;
        }
        return super.up(evt);
    }


    protected Monitor createMonitor() {
        return new FD_ICMP.PingMonitor();
    }


    /**
     * Runs InetAddress.isReachable(). Each time the command fails, we increment num_tries. If num_tries > max_tries, we
     * emit a SUSPECT message. If ping_dest changes, or we do receive traffic from ping_dest, we reset num_tries to 0.
     */
    protected class PingMonitor extends Monitor {
        long start, stop;

        public void run() {
            if(ping_dest == null) {
                if(log.isWarnEnabled())
                    log.warn("ping_dest is null: members=" + members + ", pingable_mbrs=" +
                            pingable_mbrs + ", local_addr=" + local_addr);
                return;
            }

            // 1. execute ping command
            InetAddress host=ping_dest instanceof IpAddress? ((IpAddress)ping_dest).getIpAddress() : null;
            if(host == null)
                throw new IllegalArgumentException("ping_dest is not of type IpAddress - FD_ICMP only works with these");
            try {
                if(log.isTraceEnabled())
                    log.trace("pinging " + host + " (ping_dest=" + ping_dest + ") using interface " + intf);
                start=System.currentTimeMillis();
                Boolean rc=(Boolean)is_reacheable.invoke(host, intf, new Integer(ttl), new Integer((int)timeout));
                stop=System.currentTimeMillis();
                num_heartbeats++;
                if(rc.booleanValue()) { // success
                    num_tries=0;
                    if(log.isTraceEnabled())
                        log.trace("successfully received response from " + host + " (after " + (stop-start) + "ms)");
                }
                else { // failure
                    num_tries++;
                    if(log.isDebugEnabled())
                        log.debug("could not ping " + ping_dest + " (tries=" + num_tries + ") after " + (stop-start) + "ms)");
                }

                if(num_tries >= max_tries) {
                    if(log.isDebugEnabled())
                        log.debug("[" + local_addr + "]: could not ping " + ping_dest + " for " + (num_tries +1) +
                                " times (" + ((num_tries+1) * timeout) + " milliseconds), suspecting it");
                    // broadcast a SUSPECT message to all members - loop until
                    // unsuspect or view change is received
                    bcast_task.addSuspectedMember(ping_dest);
                    num_tries=0;
                    if(stats) {
                        num_suspect_events++;
                        suspect_history.add(ping_dest);
                    }
                }
            }
            catch(Exception ex) {
                if(log.isErrorEnabled())
                    log.error("failed pinging " + ping_dest, ex);
            }
        }
    }
}
