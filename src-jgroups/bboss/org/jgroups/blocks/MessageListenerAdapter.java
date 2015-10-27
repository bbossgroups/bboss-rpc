package bboss.org.jgroups.blocks;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;

import bboss.org.jgroups.ExtendedMessageListener;
import bboss.org.jgroups.Message;
import bboss.org.jgroups.MessageListener;

/**
 * This class provides multiplexing possibilities for {@link MessageListener}
 * instances. Usually, we have more than one instance willing to listen to 
 * incoming messages, but only one that can produce state for group. 
 * {@link PullPushAdapter} allows only one instance of {@link MessageListener}
 * to be registered for message notification. With help of this class you
 * can overcome this limitation.
 * 
 * @author Roman Rokytskyy (rrokytskyy@acm.org)
 */
public class MessageListenerAdapter implements ExtendedMessageListener {
    
    protected MessageListener stateListener;
    
    protected final HashSet messageListeners = new HashSet();
    
    // we need this cache, because every call to messageListeners.iterator()
    // would generate few new objects, but iteration over the cache would not.
    protected MessageListener[] messageListenersCache = new MessageListener[0];
    
    /**
     * Create default instance of this class. Newly created instance will have 
     * no message or state listeners. You have to use 
     * {@link #addMessageListener(MessageListener)} or 
     * {@link #removeMessageListener(MessageListener)} to add or remove message
     * listeners, and {@link #setStateListener(MessageListener)} to set listener
     * that will participate in state transfer.
     */    
    public MessageListenerAdapter() {
        this(null);
    }

    /**
     * Create instance of this class. <code>mainListener</code> is a main 
     * listener instance that received message notifications and can get and 
     * set group state.
     * 
     * @param mainListener instance of {@link MessageListener} that will 
     * provide state messages.
     */
    public MessageListenerAdapter(MessageListener mainListener) {
        if (mainListener != null) {
            stateListener = mainListener;
            addMessageListener(mainListener);
        }
    }

    /**
     * Get state from state listener if present.
     * 
     * @return current state of the group state or <code>null</code> if no state 
     * listeners were registered.
     */
    public byte[] getState() {
        if (stateListener != null)
            return stateListener.getState();
        else
            return null;
    }


    public byte[] getState(String state_id) {
        if(stateListener == null)
            return null;
        if(stateListener instanceof ExtendedMessageListener) {
            return ((ExtendedMessageListener)stateListener).getState(state_id);
        }
        else {
            return stateListener.getState();
        }
    }
    
    public void getState(OutputStream ostream) {
    	if (stateListener instanceof ExtendedMessageListener) {
			((ExtendedMessageListener) stateListener).getState(ostream);
		}		
	}

	public void getState(String state_id, OutputStream ostream) {
		if (stateListener instanceof ExtendedMessageListener && state_id!=null) {
			((ExtendedMessageListener) stateListener).getState(state_id,ostream);
		}		
	}

    /**
     * Receive message from group. This method will send this message to each 
     * message listener that was registered in this adapter.
     * 
     * @param msg message to distribute within message listeners.
     */
    public void receive(Message msg) {
        for (int i = 0; i < messageListenersCache.length; i++) 
            messageListenersCache[i].receive(msg);
    }

    /**
     * Set state of ths group. This method will delegate call to state listener
     * if it was previously registered.
     */
    public void setState(byte[] state) {
        if (stateListener != null)
            stateListener.setState(state);
    }

    public void setState(String state_id, byte[] state) {
        if(stateListener != null) {
            if(stateListener instanceof ExtendedMessageListener) {
                ((ExtendedMessageListener)stateListener).setState(state_id, state);
            }
            else {
                stateListener.setState(state);
            }
        }
    }
    

	public void setState(InputStream istream) {
		if (stateListener instanceof ExtendedMessageListener) {
			((ExtendedMessageListener) stateListener).setState(istream);
		}
	}

	public void setState(String state_id, InputStream istream) {
		if (stateListener instanceof ExtendedMessageListener && state_id != null) {
			((ExtendedMessageListener) stateListener).setState(state_id,istream);
		}		
	}

    /**
     * Add message listener to this adapter. This method registers 
     * <code>listener</code> for message notification.
     * <p>
     * Note, state notification will not be used.
     */
    public final synchronized void addMessageListener(MessageListener listener) {
        if (messageListeners.add(listener))
            messageListenersCache = 
                (MessageListener[])messageListeners.toArray(
                    new MessageListener[messageListeners.size()]);
    }
    
    /**
     * Remove message listener from this adapter. This method deregisters 
     * <code>listener</code> from message notification.
     */
    public synchronized void removeMessageListener(MessageListener listener) {
        if (messageListeners.remove(listener))
            messageListenersCache = 
                (MessageListener[])messageListeners.toArray(
                    new MessageListener[messageListeners.size()]);
    }
    
    /**
     * Register <code>listener</code> for state notification events. There can
     * be only one state listener per adapter.
     */
    public void setStateListener(MessageListener listener) {
        stateListener = listener;
    }
}
