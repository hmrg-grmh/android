package mega.privacy.android.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.utils.Util;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApiAndroid;

import static mega.privacy.android.app.utils.JobUtil.scheduleCUJob;

public class NetworkStateReceiver extends BroadcastReceiver {

    protected List<NetworkStateReceiverListener> listeners;
    protected Boolean connected;

    Handler handler = new Handler();

    private MegaChatApiAndroid megaChatApi;
    private MegaApiAndroid megaApi;

    public NetworkStateReceiver() {
        listeners = new ArrayList<NetworkStateReceiverListener>();
        connected = null;
    }

    public void onReceive(Context context, Intent intent) {
        if(intent == null || intent.getExtras() == null)
            return;

        final Context c = context;

        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = manager.getActiveNetworkInfo();

        if(ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {

            log("getState = "+ni.getState());
            MegaApplication mApplication = ((MegaApplication)context.getApplicationContext());
            megaApi = mApplication.getMegaApi();

            if(Util.isChatEnabled()){
                megaChatApi = mApplication.getMegaChatApi();
            }
            else{
                megaChatApi=null;
            }

            String previousIP = mApplication.getLocalIpAddress();
            String currentIP = Util.getLocalIpAddress();
            if (previousIP == null || (previousIP.length() == 0) || (previousIP.compareTo("127.0.0.1") == 0))
            {
                mApplication.setLocalIpAddress(currentIP);
            }
            else if ((currentIP != null) && (currentIP.length() != 0) && (currentIP.compareTo("127.0.0.1") != 0) && (currentIP.compareTo(previousIP) != 0))
            {
                mApplication.setLocalIpAddress(currentIP);
                log("reconnect and retryPendingConnections");
                megaApi.reconnect();

                if (megaChatApi != null){
                    megaChatApi.retryPendingConnections(true, null);
                }
            }
            else{

                log("retryPendingConnections");
                megaApi.retryPendingConnections();

                if (megaChatApi != null){
                    megaChatApi.retryPendingConnections(false, null);
                }
            }

            connected = true;
            scheduleCUJob(c);
        } else if(intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,Boolean.FALSE)) {
            connected = false;
        }

        notifyStateToAll();
    }

    private void notifyStateToAll() {
        for(NetworkStateReceiverListener listener : listeners)
            notifyState(listener);
    }

    private void notifyState(NetworkStateReceiverListener listener) {
        if(connected == null || listener == null)
            return;

        if(connected == true)
            listener.networkAvailable();
        else
            listener.networkUnavailable();
    }

    public void addListener(NetworkStateReceiverListener l) {
        listeners.add(l);
        notifyState(l);
    }

    public void removeListener(NetworkStateReceiverListener l) {
        listeners.remove(l);
    }

    public interface NetworkStateReceiverListener {
        public void networkAvailable();
        public void networkUnavailable();
    }

    public static void log(String message) {
        Util.log("NetworkStateReceiver", message);
    }
}