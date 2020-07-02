package dcv.finaltest.configuration;
import android.nfc.Tag;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

import dcv.finaltest.configuration.IConfigurationService;
import dcv.finaltest.property.PropertyEvent;

public class ConfigurationService extends IConfigurationService.Stub{
    private static final String TAG = "ConfigurationService";
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Map<Integer, Boolean> map = new HashMap<>();

    public ConfigurationService() {
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        map.put(CONFIG_CONSUMPTION, new Boolean(true));
        map.put(CONFIG_DISTANCE, new Boolean(true));
        map.put(CONFIG_RESET, new Boolean(true));
    }

    @Override
    public boolean isSupport(int configID) throws RemoteException {
        Boolean isSupport = map.get(configID);
        return isSupport == null ? false : isSupport;
    }
}
