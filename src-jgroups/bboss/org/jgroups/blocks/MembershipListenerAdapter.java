package bboss.org.jgroups.blocks;

import java.util.HashSet;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.ExtendedMembershipListener;
import bboss.org.jgroups.MembershipListener;
import bboss.org.jgroups.View;

/**
 * This class provides multiplexing possibilities for {@link MembershipListener}
 * instances. Usually, we have more than one instance willing to listen to 
 * membership messages. {@link PullPushAdapter} allows only one instance of 
 * {@link MembershipListener} to be registered for message notification. With 
 * help of this class you can overcome this limitation.
 * 
 * @author Roman Rokytskyy (rrokytskyy@acm.org)
 */

public class MembershipListenerAdapter implements MembershipListener {
    
    protected final HashSet membershipListeners = new HashSet();
    protected MembershipListener[] membershipListenersCache = 
        new MembershipListener[0];

    /**
     * Notify membership listeners to temporarily stop sending messages into 
     * a channel. This method in turn calls same method of all registered 
     * membership listener.
     */
    public void block() {
        for(int i = 0; i < membershipListenersCache.length; i++)
            membershipListenersCache[i].block();
    }

    public void unblock() {
        for(int i = 0; i < membershipListenersCache.length; i++) {
            if(membershipListenersCache[i] instanceof ExtendedMembershipListener)
                ((ExtendedMembershipListener)membershipListenersCache[i]).unblock();
        }
    }


    /**
     * Notify membership listener that some node was suspected. This method
     * in turn passes suspected member address to all registered membership 
     * listeners.
     */
    public void suspect(Address suspected_mbr) {
        for(int i = 0; i < membershipListenersCache.length; i++)
            membershipListenersCache[i].suspect(suspected_mbr);
    }

    /**
     * Notify membership listener that new view was accepted. This method in 
     * turn passes new view to all registered membership listeners.
     */
    public void viewAccepted(View new_view) {
        for(int i = 0; i < membershipListenersCache.length; i++)
            membershipListenersCache[i].viewAccepted(new_view);
    }

    /**
     * Add membership listener to this adapter. This method registers
     * <code>listener</code> to be notified when membership event is generated.
     * 
     * @param listener instance of {@link MembershipListener} that should be
     * added to this adapter.
     */
    public synchronized void addMembershipListener(MembershipListener listener) {
        if (membershipListeners.add(listener))
            membershipListenersCache = 
                (MembershipListener[])membershipListeners.toArray(
                    new MembershipListener[membershipListeners.size()]);
    }

    /**
     * Remove membership listener from this adapter. This method deregisters
     * <code>listener</code> from notification when membership event is generated.
     * 
     * @param listener instance of {@link MembershipListener} that should be
     * removed from this adapter.
     */
    public synchronized void removeMembershipListener(MembershipListener listener) {
        if (membershipListeners.remove(listener))
            membershipListenersCache = 
                (MembershipListener[])membershipListeners.toArray(
                    new MembershipListener[membershipListeners.size()]);

    }
    
}