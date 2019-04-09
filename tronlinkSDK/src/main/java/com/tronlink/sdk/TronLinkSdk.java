package com.tronlink.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tron.wallet.bussiness.sdk.service.ITronSDKInterface;
import com.tronlink.sdk.bean.Account;
import com.tronlink.sdk.bean.Param;
import com.tronlink.sdk.bean.ResourceMessage;
import com.tronlink.sdk.download.DownLoadActivity;
import com.tronlink.sdk.sdkinterface.ITronLinkSdk;
import com.tronlink.sdk.utils.AppFrontBackUtils;
import com.tronlink.sdk.utils.AppUtils;

import java.util.List;

import static android.content.Context.BIND_AUTO_CREATE;

public class TronLinkSdk implements ITronLinkSdk {
    public static final String INTENT_LOGIN_RESULT = "intent_address_result";
    public static final String INTENT_PAY_RESULT = "pay_result";//1是白名单

    public static final String INTENT_ACTION = "intent_action";
    public static final String INTENT_ACTION_LOGIN = "intent_action_login";
    public static final String INTENT_ACTION_PAY = "intent_action_pay";
    public static final String INTENT_ACTION_TRIGGER_CONTRACT = "intent_action_trigger_contract";


    public static final int INTENT_LOGIN_REQUESTCODE = 10001;
    public static final int INTENT_PAY_REQUESTCODE = 10002;
    public static final int INTENT_TRIGGER_CONTRACT_REQUESTCODE = 10003;

    private static final String INTENT_TRANSACTION_BYTES = "intent_transaction_byte";


    private static final String INTENT_PARAM_TRIGGER_CONTRACT = "intent_param_trigger_contract";
    private static final String INTENT_PARAM_WALLETNAME = "intent_param_wallet_name";

    private static final String ENTER_URI = "tronlink://account/enter";
    private ITronSDKInterface mStub;
    private static final String TAG = "TronLinkSdk";
    private Context mApplication;

    private static class SingletonHolder {
        private static final TronLinkSdk INSTANCE = new TronLinkSdk();
    }

    private TronLinkSdk() {
    }

    public static final ITronLinkSdk getInstance() {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public void register(Application application) {
        mApplication = application;
        AppFrontBackUtils helper = new AppFrontBackUtils();
        helper.register(application, new AppFrontBackUtils.OnAppStatusListener() {
            @Override
            public void onFront() {
                //应用切到前台处理
                if(mStub==null)
                    bindService();
            }

            @Override
            public void onBack() {
                //应用切到后台处理

            }
        });

    }

    private void bindService(){
        Intent intent = new Intent();
        //由于是隐式启动Service 所以要添加对应的action，A和之前服务端的一样。
        intent.setAction("com.tronlink.wallet.TronSDKService");
        //android 5.0以后直设置action不能启动相应的服务，需要设置packageName或者Component。
        intent.setPackage("com.tronlink.wallet"); //packageName 需要和服务端的一致.
        mApplication.bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void unRegister(Context context) {
        context.unbindService(serviceConnection);
    }

    @Override
    public ResourceMessage getResourceMessage(String address, boolean isBase58) {
        ResourceMessage resourceMessage = null;
        if (adjustNotEmpty()) {
            String jsonStr = null;
            try {
                jsonStr = mStub.getResourceMessageJsonStr(address, isBase58);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "resourceMessage:" + jsonStr);
            if (!TextUtils.isEmpty(jsonStr)) {
                resourceMessage = new Gson().fromJson(jsonStr, ResourceMessage.class);
            }
        }
        return resourceMessage;
    }

    @Override
    public Account getAccount(String address, boolean isBase58) {
        Account account = null;
        if (adjustNotEmpty()) {
            String jsonStr = null;
            try {
                jsonStr = mStub.getAccountJsonStr(address, isBase58);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "resourceMessage:" + jsonStr);
            if (!TextUtils.isEmpty(jsonStr)) {
                account = new Gson().fromJson(jsonStr, Account.class);

            }
        }
        return account;
    }

    @Override
    public double getBalanceTrx(String address, boolean isBase58) {
        if (adjustNotEmpty()) {
            try {
                return mStub.getBalanceTrx(address, isBase58);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    @Override
    public byte[] createTrxTransaction(String fromAddress, String toAddress, double amount) {
        if (adjustNotEmpty()) {
            try {
                return mStub.createTrxTransaction(fromAddress, toAddress, amount);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public byte[] createTrc10Transaction(String fromAddress, String toAddress, double amount, String id) {
        if (adjustNotEmpty()) {
            try {
                return mStub.createTrc10Transaction(fromAddress, toAddress, amount, id);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public byte[] createTrc20Transaction(String fromAddress, String toAddress, double amount, int precision, String contractAddress) {
        if (adjustNotEmpty()) {
            try {
                return mStub.createTrc20Transaction(fromAddress, toAddress, amount, precision, contractAddress);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public byte[] hashOperation(String hashStr) {
        if (adjustNotEmpty()) {
            try {
                return mStub.hashOperation(hashStr);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private boolean adjustNotEmpty() {
        if (checkIsInstall()) {
            if (mStub == null) {
                AppUtils.jumpStartInterface(mApplication);
                Log.d(TAG, "mStub is null");
                return false;
            }
        }
        return true;
    }

    private boolean checkIsInstall() {
        Intent intent = new Intent();
        intent.setData(Uri.parse(ENTER_URI));
        intent.putExtra(INTENT_ACTION, INTENT_ACTION_LOGIN);
        if(!AppUtils.isAppInstalled(mApplication)){
            goToDownloadPage();
        }
        else if (!AppUtils.isAppInstalled2(mApplication, intent)) {
            //未安装app or 版本不支持schema
            goToDownloadPage();
            Toast.makeText(mApplication, mApplication.getResources().getString(R.string.sdk_version_low_tip), Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void goToDownloadPage(){
        Intent in = new Intent(mApplication, DownLoadActivity.class);
        boolean isActivity = mApplication instanceof Activity;
        if (!isActivity) {
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        mApplication.startActivity(in);
    }

    @Override
    public void authLogin(Activity activity) {
        Intent intent = new Intent();
        intent.setData(Uri.parse(ENTER_URI));
        intent.putExtra(INTENT_ACTION, INTENT_ACTION_LOGIN);
        if (AppUtils.isAppInstalled2(activity, intent)) {
            activity.startActivityForResult(intent, INTENT_LOGIN_REQUESTCODE);
        } else {
            //未安装app or 版本不支持schema
            Intent in = new Intent(activity, DownLoadActivity.class);
            activity.startActivity(in);
        }
    }

    @Override
    public void toPay(Activity activity, byte[] transactionBytes, String walletName) {
        Intent intent = new Intent();
        intent.setData(Uri.parse(ENTER_URI));
        intent.putExtra(INTENT_ACTION, INTENT_ACTION_PAY);
        intent.putExtra(INTENT_TRANSACTION_BYTES, transactionBytes);
        intent.putExtra(INTENT_PARAM_WALLETNAME, walletName);
        if (AppUtils.isAppInstalled2(activity, intent)) {
            activity.startActivityForResult(intent, INTENT_PAY_REQUESTCODE);
        } else {
            //未安装app or 版本不支持schema
            Intent in = new Intent(activity, DownLoadActivity.class);
            activity.startActivity(in);
        }
    }

    @Override
    public void toPay(Activity activity, String transtionJson, String walletName){
        Intent intent = new Intent();
        intent.setData(Uri.parse(ENTER_URI));
        intent.putExtra(INTENT_ACTION, INTENT_ACTION_TRIGGER_CONTRACT);
        intent.putExtra(INTENT_PARAM_TRIGGER_CONTRACT, transtionJson);
        intent.putExtra(INTENT_PARAM_WALLETNAME, walletName);
        if (AppUtils.isAppInstalled2(activity, intent)) {
            activity.startActivityForResult(intent, INTENT_TRIGGER_CONTRACT_REQUESTCODE);
        } else {
            //未安装app or 版本不支持schema
            Intent in = new Intent(activity, DownLoadActivity.class);
            activity.startActivity(in);
        }
    }

    @Override
    public String triggerContract(String fromAddress, String toAddress, String contractAddress,
                                String methodName, List<Param> params,
                                String freeLimit, long amount) {
        String json = null;
        if (params != null)
            json = new Gson().toJson(params);
        try {
            return mStub.triggerContract(fromAddress, toAddress, contractAddress, methodName,
                    json, freeLimit, amount);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //调用asInterface()方法获得IMyAidlInterface实例
            mStub = ITronSDKInterface.Stub.asInterface(service);
//            mStub.register();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

}
