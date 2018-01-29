package newcity56.helper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.conn.BleCharacterCallback;
import com.clj.fastble.conn.BleGattCallback;
import com.clj.fastble.data.ScanResult;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.ListScanCallback;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import newcity56.Listener.OnBindSendResultListener;
import newcity56.utils.ByteUtils;
import newcity56.utils.Map2JsonUtils;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by dxy
 * 绑定订单 查询设备数据
 */

public class BindSendHelper implements City56Helper {
    String TAG = "OrderbindHelper";
    private Context c;
    private int packNo;
    public int PACKAGELEN = 20;
    private OnBindSendResultListener onBindResultListener;
    private static final String seuiec = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String t1111 = "0000ffe1-0000-1000-8000-00805f9b34fb";
    BleManager bleManager;
    private BluetoothAdapter mBluetoothAdapter = null;
    private int TIMEOUT = 15;
    private int commStatus = 0;//0-等待握手1-握手返回，等待确认2-请求数据，等待返回3-请求阈值等待返回
    private String bleMac ;
    private int ErroCode100 = 100;// 输入蓝牙sn错误
    private int ErroCode101 = 101;//未发现蓝牙设备
    private int ErroCode102 = 102;//service = null连接失败...稍后再试
    private int ErroCode103 = 103;//超时
    private int ErroCode104 = 104;//连接中断
    private int ErroCode105 = 105;//温度计时差相差五分钟
    private int ErroCode106 = 106;//温度计返回数据错误，请重新连接
    private int ErroCode107 = 107;//连接错误
    private int ErroCode108 = 108;//不允许重复绑定运单
    public static final int ACCOUNTLEN = 16;
    public static final int TRANSPORTLEN = 20;
    int dataIndex;
    Date orderCreateDate,boxBindDate;
    private Handler mHandler;
    private Subscription subscripe;

    private StringBuilder stringBuilder = new StringBuilder();
    private String cmd59Order;//下发运单号命令文本，转换后发送给设备蓝牙的运单号，最大支持20位，转换为ascii后40位长，右补0
    private String account;
    public BindSendHelper(Context c, OnBindSendResultListener onBindResultListener) {
        this.c = c;
        this.onBindResultListener = onBindResultListener;
        bleManager = new BleManager(c);
        this.mHandler = new Handler(Looper.getMainLooper());
        init();
    }

    public int getTIMEOUT() {
        return TIMEOUT;
    }

    public void setTIMEOUT(int TIMEOUT) {
        this.TIMEOUT = TIMEOUT;
    }

    private void init() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();

        }
    }



    public void cnDeviceBle(final String searchCode, final String num,int dataIndex, String login,Date orderCreateDate,Date boxBindDate) {
        if (TextUtils.isEmpty(searchCode)||TextUtils.isEmpty(login)) {
            onBindResultListener.onFail(666);
            return;
        }
        if(orderCreateDate==null||boxBindDate==null){
            onBindResultListener.onFail(666);
            return;
        }
        onBindResultListener.onBegin();
        this.dataIndex = dataIndex;
        this.orderCreateDate =orderCreateDate;
        this.boxBindDate = boxBindDate;
        cmd59Order = genCmd59Order(num);
        account = genAccout(login);
        bleManager.scanDevice(new ListScanCallback(1000 * TIMEOUT) {
            @Override
            public void onScanning(ScanResult result) {
                if (result.getDevice().getName() != null && result.getDevice().getName().contains(searchCode)) {
                    bleManager.cancelScan();
                    bleManager.connectDevice(result, true, new BleGattCallback() {
                        @Override
                        public void onNotFoundDevice() {

                        }

                        @Override
                        public void onFoundDevice(ScanResult scanResult) {

                        }

                        @Override
                        public void onConnectSuccess(BluetoothGatt gatt, int status) {
                            gatt.discoverServices();
                            Log.d(TAG, "连接蓝牙成功，查找服务");
                        }

                        @Override
                        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                            super.onServicesDiscovered(gatt, status);
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                BluetoothGattService service = gatt.getService(UUID.fromString(seuiec));
                                if (service == null) {
                                    bleManager.closeBluetoothGatt();
                                    onBindResultListener.onFail(ErroCode102);
//                                    cnDeviceBle(searchCode);//
                                    return;
                                }else {
                                    communicate(0, true);//连接成功
                                }

                            }
                        }

                        @Override
                        public void onConnectFailure(BleException exception) {
                            onBindResultListener.onFail(ErroCode107);
                        }


                    });
                }
            }

            @Override
            public void onScanComplete(ScanResult[] results) {
                Log.d(TAG, "Scan completed");
            }

            @Override
            public void onScanTimeout() {
                super.onScanTimeout();
                onBindResultListener.onFail(ErroCode101);
            }
        });
    }

    Runnable bleReadTimeout = new Runnable() {
        @Override
        public void run() {
            onBindResultListener.onFail(ErroCode103);//"通信超时，请稍后尝试"
            Observable.timer(3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                @Override
                public void call(Long aLong) {
                    bleManager.closeBluetoothGatt();
                }
            });

        }
    };

    void communicate(final int cmdStatus, final boolean cleanBuffer) {
        Observable.timer(1, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                commStatus = cmdStatus;
                if (cleanBuffer) stringBuilder.setLength(0);
                final String deviceMac = bleMac;
                switch (cmdStatus) {
                    case 0://等待接收握手第一帧
                    {
                        Log.e(TAG, "开始监听AA77:" + System.currentTimeMillis());
                        bleManager.notify(seuiec, t1111, bleCallback);
                    }
                    break;
                    case 1://发送握手确认帧
                    {
                        bleManager.writeDevice(seuiec, t1111, ByteUtils.cmdString2Bytes("CC9903" + deviceMac + "02EF",true), bleCallback);
                    }
                    break;
                    case 2: {//等待接收握手第二帧
                        bleManager.notify(seuiec, t1111, bleCallback);
                    }
                    break;
                    case 3://发送AA55的59命令，下发运单号
                    {
                        String cmd = genCmd59String();
                        Log.d(TAG,"59cmd:" + cmd);
                        int endPos = (cmd.length() > (packNo + 1) * PACKAGELEN * 2) ? (packNo + 1) * PACKAGELEN * 2 : cmd.length();
                        String send = cmd.substring(packNo * PACKAGELEN * 2, endPos);
                        Log.d(TAG, "this time send:" + send);
                        bleManager.writeDevice(seuiec, t1111, ByteUtils.hexString2Bytes(send), bleCallback);

                    }
                    break;
                    case 4: {//等待接收数据
                        Log.e(TAG, "wait for receive data...");
                        bleManager.notify(seuiec, t1111, bleCallback);
                    }
                    break;
                    case 5://发送53断开连接
                    {
                        bleManager.writeDevice(seuiec, t1111, ByteUtils.cmdString2Bytes("AA5504" + deviceMac + "5306ef",true), bleCallback);
                    }
                    break;
                }
                if (cleanBuffer && commStatus!=5) {
                    Log.e(TAG, "超时启动：" + commStatus + " ti:" + System.currentTimeMillis());
                    mHandler.postDelayed(bleReadTimeout, 1000 * 45);
                }
            }
        });

    }

    private String genCmd59Order(String num) {
        StringBuilder cmd = new StringBuilder();
        //转换asc字符串，总长度20位，运单号长度不足以0右补齐
        String ascS = string2Asc(num);
        StringBuilder sb = new StringBuilder(ascS);
        while (sb.length() < TRANSPORTLEN * 2) {
            sb.append("0");
        }
        cmd.append(sb.toString());
        Date bindDate = orderCreateDate;// Date bindDate = transport_info.getCreate_date();
        cmd.append(Map2JsonUtils.date2HexString(bindDate));

        return cmd.toString();

    }
    private String genCmd59String() {

        StringBuilder cmd = new StringBuilder("AA5530");
        cmd.append(bleMac).append("59").append(cmd59Order);
        long diff = orderCreateDate.getTime() - boxBindDate.getTime();
        long elapseMinute = diff / (1000 * 60 * 5);
       int  dataIndex2 = (dataIndex + (int) elapseMinute + 1) % 65535;
        StringBuilder diSb = new StringBuilder(Integer.toHexString(dataIndex2));
        while (diSb.length() < 4) {
            diSb.insert(0, "0");
        }
        cmd.append(diSb.toString()).append(account).append("00EF");
        byte[] bArr = ByteUtils.cmdString2Bytes(cmd.toString(),true);
        return ByteUtils.bytes2HexString(bArr);
    }

    private static String string2Asc(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            int c = str.charAt(i);
            sb.append(Integer.toHexString(c));
        }
        return sb.toString();
    }
    private BleCharacterCallback bleCallback = new BleCharacterCallback() {
        @Override
        public void onSuccess(BluetoothGattCharacteristic characteristic) {
            byte[] b = characteristic.getValue();
            stringBuilder.append(ByteUtils.bytes2HexString(b).toLowerCase());
            String sReceived = stringBuilder.toString();
            Log.d(TAG, "received:" + sReceived);
            switch (commStatus) {
                case 0: {
                    int start = sReceived.indexOf("aa77");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        bleMac = data.substring(6, 12);
                        subscripe = Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                communicate(1, true);
                            }
                        });
                    }
                }
                break;
                case 1: {
                    int start = sReceived.indexOf("cc99");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "send cc99 success");
                        communicate(2, true);
                    }
                }
                break;
                case 2: {
                    int start = sReceived.indexOf("dd88");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "received dd88");
                        Observable.timer(500, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                packNo = 0;
                                communicate(3, true);
                            }
                        });
                    }
                }
                break;
                case 3: {
                    int start = sReceived.indexOf("aa55");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            packNo++;
                            Observable.timer(200, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                @Override
                                public void call(Long aLong) {
                                    communicate(3, false);
                                }
                            });
                        } else {
                            Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                            mHandler.removeCallbacks(bleReadTimeout);
                            Log.e(TAG, "send aa55 59 success");
//                            try {
//                                DeviceName dn = new DeviceName();
//                                dn.setCompany_code(list.get(position111).getTransport_info_id());
//                                dn.setDevice_code(list.get(position111).getDevice_code());
//                                dn.setName(data);
//                                dn.setUpdate_date(new Date().toString());
//                                MyApplication.getInstance().getDb().saveOrUpdate(dn);
                                communicate(4, true);
//                            } catch (DbException e) {
//                                e.printStackTrace();
//                            }

                        }
                    }
                }
                break;
                case 4: {
                    int start = sReceived.indexOf("dd66");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        int dealOk = da[3] & 0xff;
                        if (dealOk == 1) {
//                            try {
//                                list.get(position111).setIsok(1);
//                                MyApplication.getInstance().getDb().saveOrUpdate(list.get(position111));
//                                DeviceName dn = new DeviceName();
//                                dn.setCompany_code(list.get(position111).getTransport_info_id());
//                                dn.setDevice_code(list.get(position111).getDevice_code());
//                                dn.setName(data);
//                                dn.setUpdate_date(new Date().toString());
//                                MyApplication.getInstance().getDb().saveOrUpdate(dn);
//                            } catch (DbException e) {
//                                e.printStackTrace();
//                            }
                        }
                        final int showImg = (dealOk == 1) ? 3 : 2;
                        final String tips = (dealOk == 1) ? "运单号下发成功" : "温度计不允许绑定";
                        Log.e("温度计不允许绑定",data);
                        onBindResultListener.onSucceed(dealOk+"");
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                list.get(position111).setIsshowimage(showImg);
//                                sendNumForBindAdapter.showPro();
//                                Toast.makeText(SendNumForBindActivity.this, tips, Toast.LENGTH_LONG).show();
                                Observable.timer(200, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                    @Override
                                    public void call(Long aLong) {
                                        communicate(5, true);
                                    }
                                });
//                            }
//                        });
                    }
                }
                break;
                case 5: {
                    mHandler.removeCallbacks(bleReadTimeout);
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            materialDialog.dismiss();
//                            Observable.timer(200, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
//                                @Override
//                                public void call(Long aLong) {
                                    bleManager.closeBluetoothGatt();
//                                }
//                            });
//                            subscripe = Observable.timer(2, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
//                                @Override
//                                public void call(Long aLong) {
//                                    judgeWhat2Do();
//                                }
//                            });
//                        }
//                    });
                }
                break;
            }
        }

        @Override
        public void onFailure(BleException exception) {
            Log.d(TAG, exception.getCode() + exception.getDescription());
            reiniBle();
        }
    };

    /**
     * 连接异常中断，释放蓝牙
     */
    void reiniBle() {

        mHandler.removeCallbacks(bleReadTimeout);
        onBindResultListener.onFail(ErroCode104);
    }

    private String genAccout(String login) {
        String ascS = string2Asc(login);
        StringBuilder cmd = new StringBuilder(ascS);
        while (cmd.length() < ACCOUNTLEN * 2) {
            cmd.append("0");
        }
        return cmd.toString();
    }
}
