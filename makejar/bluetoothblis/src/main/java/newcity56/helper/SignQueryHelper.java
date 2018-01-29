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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import newcity56.Listener.OnSignQueryListener;
import newcity56.entity.TransportDeviceBean;
import newcity56.utils.ByteUtils;
import newcity56.utils.Map2JsonUtils;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by dxy
 * 绑定订单 查询设备数据
 */

public class SignQueryHelper implements City56Helper {
    String TAG = "OrderbindHelper";
    private Context c;
    private OnSignQueryListener onBindResultListener;
    public static final int BLE_CONN_TIMEOUT = 15;
    public static final int BLE_READ_TIMEOUT = 45;
    private static final String suurc = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String tt111 = "0000ffe1-0000-1000-8000-00805f9b34fb";
    BleManager bleManager;
    private BluetoothAdapter mBluetoothAdapter = null;
    private int TIMEOUT = 15;
    private int commStatus = 0;//0-等待握手1-握手返回，等待确认2-请求数据，等待返回3-请求阈值等待返回
    private String bleMac = "123456";
    private int ErroCode100 = 100;// 输入蓝牙sn错误
    private int ErroCode101 = 101;//未发现蓝牙设备
    private int ErroCode102 = 102;//service = null连接失败...稍后再试
    private int ErroCode103 = 103;//超时
    private int ErroCode104 = 104;//连接中断
    private int ErroCode105 = 105;//温度计时差相差五分钟
    private int ErroCode106 = 106;//温度计返回数据错误，请重新连接
    private int ErroCode107 = 107;//连接错误
    private int ErroCode108 = 108;//不允许重复绑定运单
    private Handler mHandler;
    private List<TransportDeviceBean> mBoxList = new ArrayList<TransportDeviceBean>();
    private StringBuilder stringBuilder = new StringBuilder();
    TransportDeviceBean dataBean;

    public SignQueryHelper(Context c, OnSignQueryListener onBindResultListener) {
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


    /**
     * 根据设备号查找连接设备蓝牙
     */
    public void cnDeviceBle(final String mBoxSn) {
        if (TextUtils.isEmpty(mBoxSn)) {
            onBindResultListener.onFail(666);
        }
        onBindResultListener.onBegin();
        bleManager.scanDevice(new ListScanCallback(1000 * BLE_CONN_TIMEOUT) {
            @Override
            public void onScanning(ScanResult result) {
                if (result.getDevice().getName() != null && result.getDevice().getName().contains(mBoxSn)) {
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
                        }

                        @Override
                        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                            super.onServicesDiscovered(gatt, status);
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                BluetoothGattService service = gatt.getService(UUID.fromString(suurc));
                                if (service == null) {
                                    bleManager.closeBluetoothGatt();
                                    onBindResultListener.onFail(ErroCode102);
//                                    cnDeviceBle(searchCode);//
                                    return;
                                } else {
                                    communicate(0);//连接成功
                                }
                            }
                        }

                        @Override
                        public void onConnectFailure(BleException exception) {
                            mHandler.removeCallbacks(bleReadTimeout);
                            onBindResultListener.onFail(ErroCode107);
                        }


                    });
                }
            }

            @Override
            public void onScanComplete(ScanResult[] results) {

                Log.d(TAG, "scan completed");
            }

            @Override
            public void onScanTimeout() {
                super.onScanTimeout();
                onBindResultListener.onFail(ErroCode101);
            }
        });
    }

    /**
     * 蓝牙通信流程
     *
     * @param cmdStatus
     */
    void communicate(final int cmdStatus) {
        Observable.timer(1, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                commStatus = cmdStatus;
                stringBuilder.setLength(0);
                final String deviceMac = bleMac;
                switch (cmdStatus) {
                    case 0://等待接收握手第一帧
                    {
                        bleManager.notify(suurc, tt111, bleCallback);
                    }
                    break;
                    case 1://发送握手确认帧
                    {
                        bleManager.writeDevice(suurc, tt111, ByteUtils.cmdString2Bytes("CC9903" + deviceMac + "02EF", true), bleCallback);
                    }
                    break;
                    case 2: {//等待接收握手第二帧
                        bleManager.notify(suurc, tt111, bleCallback);
                    }
                    break;
                    case 3://发送AA55的55命令，获取数据
                    {
                        bleManager.writeDevice(suurc, tt111, ByteUtils.cmdString2Bytes("AA5504" + deviceMac + "5505ef", true), bleCallback);

                    }
                    break;
                    case 4: {//等待接收数据
                        bleManager.notify(suurc, tt111, bleCallback);
                    }
                    break;
                    case 5://发送53断开连接
                    {
                        bleManager.writeDevice(suurc, tt111, ByteUtils.cmdString2Bytes("AA5504" + deviceMac + "5306ef", true), bleCallback);
                    }
                    break;
                }
                mHandler.postDelayed(bleReadTimeout, 1000 * BLE_READ_TIMEOUT);
            }
        });

    }

    /**
     * 蓝牙数据交互回调函数
     */
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
                        mHandler.removeCallbacks(bleReadTimeout);
                        bleMac = data.substring(6, 12);
                        Observable.timer(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                communicate(1);
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
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "send cc99 success");
                        communicate(2);
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
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "received dd88");
                        Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                communicate(3);
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
                            return;
                        }
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "send aa55 55 success");
                        communicate(4);
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
                        mHandler.removeCallbacks(bleReadTimeout);
                        String colDate = Map2JsonUtils.byte2DateStr(da[59], da[60], da[61], da[62], da[67], da[68]);
                        Date signDate = Map2JsonUtils.byte2Date(da[59], da[60], da[61], da[62], da[67], da[68]);

                        String t;
                        int ti = da[63] & 0xff;
                        int td = da[64] & 0xff;
                        if (ti > 127) {
                            ti = ti - 128;
                            t = "-" + ti;
                        } else {
                            t = "" + ti;
                        }
                        int dec = td * 625 / 1000;
                        t = t + "." + dec;

                        int power = da[70] & 0xff;
                        int t_alarm = da[71] & 0x0f;
                        String alarm = "正常";
                        if (t_alarm == 1) alarm = "高温告警";
                        else if (t_alarm == 8) alarm = "低温告警";

                        int dataIndex_h = da[72] & 0xff;
                        int dataIndex_l = da[73] & 0xff;
                        int dataIndex = dataIndex_h * 256 + dataIndex_l;


                        TransportDeviceBean dataBean = new TransportDeviceBean();//mBoxList.get(curConnIndex)
                        dataBean.setCrruDate(colDate);
                        dataBean.setTemperature(t);
                        dataBean.setPower(String.valueOf(power));
                        dataBean.setAddress(alarm);
                        dataBean.setAlarm(String.valueOf(t_alarm));
                        dataBean.setSignDataIndex(dataIndex);
                        dataBean.setSignDate(signDate);
                        int orderStat = da[38] & 0xff;
                        dataBean.setOrderStat(orderStat);
                        if (orderStat == 0 || orderStat == 4) {
                            dataBean.setBindNum("未绑定运单");
                        } else {
                            byte[] bOrder = ByteUtils.hexString2Bytes(data.substring(78, 118));
                            String orderNo = ByteUtils.bytes2String(bOrder);
                            dataBean.setBindNum(orderNo);
                        }

                        int hw_int = da[19] & 0xff;
                        int hw_dec = da[20] & 0xff;
                        if (hw_int > 127) {
                            int a = hw_int - 256;
                            dataBean.setHigTem(a + "." + hw_dec);
                        } else {
                            dataBean.setHigTem(hw_int + "." + hw_dec);
                        }
                        int hl_int = da[25] & 0xff;
                        int hl_dec = da[26] & 0xff;
                        if (hl_int > 127) {
                            int a = hl_int - 256;
                            dataBean.setLowTem(a + "." + hl_dec);
                        } else {
                            dataBean.setLowTem(hl_int + "." + hl_dec);
                        }
                        dataBean.setThreshold(dataBean.getLowTem() + "~" + dataBean.getHigTem() + "℃");
                        onBindResultListener.onDateSucceed(dataBean);
//                        TransportInfo_Dv dv = list_dv.get(curConnIndex);
//                        if (dv.getSignDate() == null && dv.getSignDataIndex() == 65535) {
//                            dv.setSignDataIndex(dataIndex);
//                            dv.setSignDate(signDate);
//                        }
//                        try {
//                            MyApplication.getInstance().getDb().saveOrUpdate(dv);
//                            dataBean.setCnSuccess(1);
//                        } catch (DbException e) {
//                            e.printStackTrace();
//                            dataBean.setCnSuccess(0);
//                        }
                        Observable.timer(200, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                communicate(5);
                            }
                        });
                    }
                }
                break;
                case 5: {
                    mHandler.removeCallbacks(bleReadTimeout);
                    Observable.timer(500, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                        @Override
                        public void call(Long aLong) {
                            bleManager.closeBluetoothGatt();
                        }
                    });
                }
                break;
            }
        }

        @Override
        public void onFailure(BleException exception) {
            Log.d(TAG, exception.getCode() + exception.getDescription());
            mHandler.removeCallbacks(bleReadTimeout);
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


}
