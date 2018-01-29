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

import newcity56.Listener.OnBindResultListener;
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

public class BindQueryHelper implements City56Helper {
    String TAG = "OrderbindHelper";
    private Context c;
    private OnBindResultListener onBindResultListener;
    private static final String suuec = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String tt11 = "0000ffe1-0000-1000-8000-00805f9b34fb";
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
    private Handler mHandler;
    private List<TransportDeviceBean> mBoxList = new ArrayList<TransportDeviceBean>();
    private StringBuilder stringBuilder = new StringBuilder();
    TransportDeviceBean dataBean;

    public BindQueryHelper(Context c, OnBindResultListener onBindResultListener) {
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


    public void cnDeviceBle(final String mBoxSn) {
        onBindResultListener.onBegin();
        if (!TextUtils.isEmpty(mBoxSn)) {

            //TODO 15,可配置
            bleManager.scanDevice(new ListScanCallback(1000 * TIMEOUT) {
                @Override
                public void onScanning(ScanResult result) {
                    if (result.getDevice().getName() != null && result.getDevice().getName().contains(mBoxSn)) {
                        Log.d(TAG, "found device:" + result.getDevice().getName());
                        bleManager.cancelScan();
                        bleManager.connectDevice(result, true, new BleGattCallback() {
                            @Override
                            public void onNotFoundDevice() {
                                onBindResultListener.onFail(ErroCode101);
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
                                    BluetoothGattService service = gatt.getService(UUID.fromString(suuec));
                                    if (service == null) {
                                        bleManager.closeBluetoothGatt();
                                        onBindResultListener.onFail(ErroCode102);
                                        return;
                                    } else {
                                        Log.e("gatt", gatt.toString());
                                        communicate(0);//Toast.makeText(OrderBindActivity.this, "连接成功...", Toast.LENGTH_SHORT).show();
                                    }

                                }
                            }

                            @Override
                            public void onConnectFailure(BleException exception) {
                                Log.e("eeeee", exception.toString());
                                mHandler.removeCallbacks(bleReadTimeout);
                                onBindResultListener.onFail(ErroCode102);
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
                    onBindResultListener.onFail(ErroCode103);
                }
            });

        } else {
            onBindResultListener.onFail(ErroCode100);
        }
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
                        bleManager.notify(suuec, tt11, bleCallback);
                    }
                    break;
                    case 1://发送握手确认帧
                    {
                        bleManager.writeDevice(suuec, tt11, ByteUtils.hexString2Bytes("CC9903" + deviceMac + "02EF"), bleCallback);
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(OrderBindActivity.this, "获取当前数据...",Toast.LENGTH_SHORT).show();
//                            }
//                        });
                    }
                    break;
                    case 2: {//等待接收握手第二帧
                        bleManager.notify(suuec, tt11, bleCallback);
                    }
                    break;
                    case 3://发送AA55的55命令，获取数据
                    {
                        bleManager.writeDevice(suuec, tt11, ByteUtils.hexString2Bytes("AA5504" + deviceMac + "5505ef"), bleCallback);

                    }
                    break;
                    case 4: {//等待接收数据
                        bleManager.notify(suuec, tt11, bleCallback);
                    }
                    break;
                    case 5://发送53断开连接
                    {
                        bleManager.writeDevice(suuec, tt11, ByteUtils.hexString2Bytes("AA5504" + deviceMac + "5306ef"), bleCallback);

                    }
                    break;
                }
                mHandler.postDelayed(bleReadTimeout, 1000 * TIMEOUT);
            }
        });

    }
//        });
//    }


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
                        Date d = Map2JsonUtils.byte2Date(da[7], da[8], da[9], da[10], da[11], da[12]);
                        Date now = new Date();
                        long diff = now.getTime() - d.getTime();
                        if (Math.abs(diff) > (1000 * 5 * 60)) {//系统时钟差大于5分钟
                            onBindResultListener.onFail(ErroCode105);//"温度计时间差距大于5分钟，请检查"
                            reiniBle(false);
                            return;
                        }
                        Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
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
                        Observable.timer(500, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
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
                        String deviceCode = data.substring(14, 20);
                        if (!deviceCode.matches("^[0-9]*$")) {
                            onBindResultListener.onFail(ErroCode106);
                            reiniBle(false);
                            return;
                        }
                        String colDate = "";
                        Date bindDate = new Date();
                        try {
                            colDate = Map2JsonUtils.byte2DateStr(da[59], da[60], da[61], da[62], da[67], da[68]);
                            bindDate = Map2JsonUtils.byte2Date(da[59], da[60], da[61], da[62], da[67], da[68]);
                        } catch (Exception e) {

                        }

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

                        dataBean = new TransportDeviceBean();
                        dataBean.setCode(deviceCode);
                        dataBean.setCrruDate(colDate);
                        dataBean.setTemperature(t);
                        dataBean.setPower(String.valueOf(power));
                        dataBean.setAddress(alarm);
                        dataBean.setAlarm(String.valueOf(t_alarm));
                        dataBean.setBindDataIndex(dataIndex);
                        dataBean.setBindDate(bindDate);

                        int hw_int = da[19] & 0xff;
                        int hw_dec = da[20] & 0xff;
                        if (hw_int > 127) {
                            int a = hw_int - 128;
                            dataBean.setHigTem("-" + a + "." + hw_dec);
                        } else {
                            dataBean.setHigTem(hw_int + "." + hw_dec);
                        }
                        int hl_int = da[25] & 0xff;
                        int hl_dec = da[26] & 0xff;
                        if (hl_int > 127) {
                            int a = hl_int - 128;
                            dataBean.setLowTem("-" + a + "." + hl_dec);
                        } else {
                            dataBean.setLowTem(hl_int + "." + hl_dec);
                        }
                        dataBean.setThreshold(dataBean.getLowTem() + "~" + dataBean.getHigTem() + "℃");
//                        mBoxList.add(dataBean);
                        //更新UI
                        onBindResultListener.onSucceed(dataBean);


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
                    onBindResultListener.onFinaly();
                }
                break;

            }
        }

        @Override
        public void onFailure(BleException exception) {
            Log.d(TAG, exception.getCode() + exception.getDescription());
            mHandler.removeCallbacks(bleReadTimeout);
            reiniBle(true);
        }
    };

    /**
     * 连接异常中断，释放蓝牙
     */
    void reiniBle(boolean showErr) {
        onBindResultListener.onFail(ErroCode104);//"通信异常，请稍后尝试"
        Observable.timer(3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                bleManager.closeBluetoothGatt();
            }
        });
    }

}
