package dcv.finaltest.hmiapplication;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import dcv.finaltest.configuration.ConfigurationService;
import dcv.finaltest.configuration.IConfigurationService;
import dcv.finaltest.property.IPropertyService;
import dcv.finaltest.property.PropertyService;

public class HMIService extends Service {
    public static String TAG = "HMIService";
    private HandlerThread mHandlerThread;
    private ServiceHandler mHandler;
    private IConfigurationService mConfigService;
    private IPropertyService mPropertyService;
    private IServiceInterface mService;
    private final IBinder mBinder = new LocalBinder();
    private HMIListener hmiListener = new HMIListener();

    private class HMIListener extends IHMIListener.Stub {


        @Override
        public void onDistanceUnitChanged(int distanceUnit) throws RemoteException {

        }

        @Override
        public void onDistanceChanged(double distance) throws RemoteException {

        }

        @Override
        public void OnConsumptionUnitChanged(int consumptionUnit) throws RemoteException {

        }

        @Override
        public void onConsumptionChanged(double[] consumptionList) throws RemoteException {

        }

        @Override
        public void onError(boolean isError) throws RemoteException {

        }
    }

    private class ServiceHandler extends Handler {
        private static final int MSG_REGISTER_LISTENER = 1;
        private static final int MSG_UNREGISTER_LISTENER = 2;

        ServiceHandler(Looper looper) {
            super(looper);
        }

        public void registerListener() {
            if (!sendMessage(obtainMessage(MSG_REGISTER_LISTENER))) {
                Log.e(TAG, "sendMessage Failed: MSG_REGISTER_LISTENER");
            }
        }

        public void unregisterListener() {
            if (!sendMessage(obtainMessage(MSG_UNREGISTER_LISTENER))) {
                Log.e(TAG, "sendMessage Failed: MSG_UNREGISTER_LISTENER");
            }
        }


        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_REGISTER_LISTENER) {
                try {
                    mService.registerListener(hmiListener);
                } catch (RemoteException e) {
                    Log.e(TAG, "Call registerListener failed" + e.getMessage());
                }
            } if (msg.what == MSG_UNREGISTER_LISTENER) {
                try {
                    mService.unregisterListener(hmiListener);
                    mService = null;
                } catch (RemoteException e) {
                    Log.e(TAG, "Call unregisterListener failed" + e.getMessage());
                }
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IServiceInterface.Stub.asInterface(service);
            if (mService == null) {
                Log.e(TAG, "Can not connect to " + name.getPackageName());
                return;
            }

            mHandler.registerListener();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mService != null)
                mHandler.unregisterListener();
        }
    };

    public class LocalBinder extends Binder {
        HMIService getService() {
            return HMIService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new ServiceHandler(mHandlerThread.getLooper());
        mConfigService = new ConfigurationService();
        mPropertyService = new PropertyService();
    }

    public void connectStudentService(String action, String packageName) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.setPackage(packageName);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void disconnectStudentService() {
        if (mService != null) unbindService(mConnection);
    }

    public HMIService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        String serviceName = intent.getStringExtra("service");
        if (serviceName.equals("config")) {
            Log.d(TAG, "return Config Service");
            return mConfigService.asBinder();
        }
        else if (serviceName.equals("property")) {
            Log.d(TAG, "return property Service");
            return mPropertyService.asBinder();
        }
        else if (serviceName.equals("hmi")) {
            Log.d(TAG, "return hmi Service");
            return mBinder;
        }

        else
            return null;
    }
}
