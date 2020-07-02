package dcv.finaltest.property;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PropertyService extends IPropertyService.Stub {
    private static String TAG = "PropertyService";
    private HandlerThread mHandlerThread;
    private PropertyServiceHandler mEventHandler;
    private final Map<IBinder, Client> mClientMap = new ConcurrentHashMap<>();
    private final Map<Integer, List<Client>> mPropIdClientMap = new ConcurrentHashMap<>();
    private Map<Integer, PropertyEvent> map = new HashMap<>();
    private Timer timer;
    private TimerTask timerTask;

    private static class PropertyListenerMessage {
        private int mPropId;
        private IPropertyEventListener mListener;

        PropertyListenerMessage(int propId, IPropertyEventListener listener) {
            mPropId = propId;
            mListener = listener;
        }
    };

    private class PropertyServiceHandler extends Handler {
        private static final int MSG_REGISTER_PROPERTY_LISTENER = 1;
        private static final int MSG_UNREGISTER_PROPERTY_LISTENER = 2;
        private static final int MSG_SET_PROPERTY_VALUE= 3;
        private static final int MSG_SEND_PROPERTY_VALUE= 4;

        PropertyServiceHandler(Looper looper) {
            super(looper);
        }

        public void registerPropertyListener(PropertyListenerMessage msg) {
            if (!sendMessage(obtainMessage(MSG_REGISTER_PROPERTY_LISTENER, msg))) {
                Log.e(TAG, "sendMessage Failed: MSG_REGISTER_PROPERTY_LISTENER");
            }
        }

        public void unregisterPropertyListener(PropertyListenerMessage msg) {
            if (!sendMessage(obtainMessage(MSG_UNREGISTER_PROPERTY_LISTENER, msg))) {
                Log.e(TAG, "sendMessage Failed: MSG_UNREGISTER_PROPERTY_LISTENER");
            }
        }

        public void setPropertyValue(PropertyEvent msg) {
            if (!sendMessage(obtainMessage(MSG_SET_PROPERTY_VALUE, msg))) {
                Log.e(TAG, "sendMessage Failed: MSG_SET_PROPERTY_VALUE");
            }
        }

        public void sendPropertyValue(int msg) {
            if (!sendMessage(obtainMessage(MSG_SEND_PROPERTY_VALUE, msg))) {
                Log.e(TAG, "sendMessage Failed: MSG_SET_PROPERTY_VALUE");
            }
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_REGISTER_PROPERTY_LISTENER) {
                PropertyListenerMessage message = (PropertyListenerMessage) msg.obj;
                if (message == null)
                    return;

                int propId = message.mPropId;
                IPropertyEventListener listener = message.mListener;
                registerListenerBinderLocked(propId, listener);
            } else if (msg.what == MSG_UNREGISTER_PROPERTY_LISTENER) {
                PropertyListenerMessage message = (PropertyListenerMessage) msg.obj;
                if (message == null)
                    return;

                int propId = message.mPropId;
                IPropertyEventListener listener = message.mListener;
                unregisterListenerBinderLocked(propId, listener.asBinder());
            } else if (msg.what == MSG_SET_PROPERTY_VALUE) {
                PropertyEvent message = (PropertyEvent) msg.obj;
                if (message == null)
                    return;

                setPropertyBinderLocked(message);
            } else if (msg.what == MSG_SEND_PROPERTY_VALUE) {
                int message = (int) msg.obj;
                sendPropertyBinderLocked(message);
            }
        }
    }

    private void sendPropertyBinderLocked(int propId) {
        PropertyEvent event = map.get(propId);
        if (event == null) {
            Log.e(TAG, "Does not exist property ID = " + propId);
        } else {
            List<Client> ls = mPropIdClientMap.get(propId);
            if (ls == null) {
                Log.e(TAG, "Does not exist register list of ID = " + propId);
            } else {
                for (Client client: ls) {
                    PropertyEvent newEvent = new PropertyEvent(event.getPropertyId(), event.getStatus(), event.getTimestamp(), event.getValue());
                    try {
                        client.mListener.onEvent(newEvent);
                    }catch (RemoteException ex) {
                        Log.e(TAG, "onEvent calling failed: " + ex);
                    }
                }
            }
        }

    }

    private void setPropertyBinderLocked(PropertyEvent message) {
        int propId = message.getPropertyId();
        PropertyEvent event = map.get(propId);
        if (event == null) {
            Log.e(TAG, "Does not exist property ID = " + propId);
        } else {
            event.setValue(message.getValue());
            sendPropertyBinderLocked(propId);
        }
    }

    private void unregisterListenerBinderLocked(int propId, IBinder listenerBinder) {
        Client client = mClientMap.get(listenerBinder);
        List<Client> propertyClients = mPropIdClientMap.get(propId);
        if ((client == null) || (propertyClients == null)) {
            Log.e(TAG, "unregisterListenerBinderLocked: Listener was not previously registered.");
        } else {
            propertyClients.remove(client);
            client.release();
        }
    }

    private void registerListenerBinderLocked(int propId, IPropertyEventListener listener) {
        IBinder listenerBinder = listener.asBinder();
        Client client = mClientMap.get(listenerBinder);
        if (client == null) {
            client = new Client(listener);
        }
        List<Client> clients = mPropIdClientMap.get(propId);
        if (clients == null) {
            clients = new CopyOnWriteArrayList<Client>();
            mPropIdClientMap.put(propId, clients);
        }
        if (!clients.contains(client)) {
            clients.add(client);
        }

        PropertyEvent event = map.get(propId);
        if (event != null) {
            PropertyEvent newEvent = new PropertyEvent(event.getPropertyId(), event.getStatus(), event.getTimestamp(), event.getValue());
            try {
                listener.onEvent(newEvent);
            }catch (RemoteException ex) {
                Log.e(TAG, "onEvent calling failed: " + ex);
            }

        }
    }

    private class Client implements IBinder.DeathRecipient {
        private final IPropertyEventListener mListener;
        private final IBinder mListenerBinder;

        Client(IPropertyEventListener listener) {
            mListener = listener;
            mListenerBinder = listener.asBinder();
            try {
                mListenerBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "\"Failed to link death for recipient. " + e);
                throw new IllegalStateException("Client already dead", e);
            }
            mClientMap.put(mListenerBinder, this);
        }

        @Override
        public void binderDied() {
            Log.d(TAG, "binderDied " + mListenerBinder);
            release();
        }

        void release() {
            mListenerBinder.unlinkToDeath(this, 0);
            mClientMap.remove(mListenerBinder);
        }
    }

    public PropertyService() {
        map.put(PROP_CONSUMPTION_UNIT, new PropertyEvent(PROP_CONSUMPTION_UNIT, PropertyEvent.STATUS_AVAILABLE, 0, 0));
        map.put(PROP_CONSUMPTION_VALUE, new PropertyEvent(PROP_CONSUMPTION_VALUE, PropertyEvent.STATUS_AVAILABLE, 0, 0.0f));
        map.put(PROP_DISTANCE_UNIT, new PropertyEvent(PROP_DISTANCE_UNIT, PropertyEvent.STATUS_AVAILABLE, 0, 0));
        map.put(PROP_DISTANCE_VALUE, new PropertyEvent(PROP_DISTANCE_VALUE, PropertyEvent.STATUS_AVAILABLE, 0, 0.0f));
        map.put(PROP_RESET, new PropertyEvent(PROP_RESET, PropertyEvent.STATUS_AVAILABLE, 0, false));

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mEventHandler = new PropertyServiceHandler(mHandlerThread.getLooper());

        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                PropertyEvent event = map.get(PROP_CONSUMPTION_VALUE);
                Random ran = new Random();
                float newvalue = ((float) event.getValue()) + (float)ran.nextDouble() ;
                PropertyEvent newEvent = new PropertyEvent(event.getPropertyId(), event.getStatus(), event.getTimestamp(), newvalue);
                mEventHandler.setPropertyValue(newEvent);
            }
        };
        timer.schedule(timerTask, 500, 500);

    }

    @Override
    public void registerListener(int propID, IPropertyEventListener callback) throws RemoteException {
        Log.d(TAG, "PropID " + propID + " has been registered");
        mEventHandler.registerPropertyListener(new PropertyListenerMessage(propID, callback));
    }

    @Override
    public void unregisterListener(int propID, IPropertyEventListener callback) throws RemoteException {
        Log.d(TAG, "PropID " + propID + " has been unregistered");
        mEventHandler.unregisterPropertyListener(new PropertyListenerMessage(propID,  callback));
    }

    @Override
    public void setProperty(int prodID, PropertyEvent value) throws RemoteException {
        Log.d(TAG, "PropID " + prodID + " has been set");
        mEventHandler.setPropertyValue(value);
    }

}
