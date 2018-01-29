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

import newcity56.Listener.OnSignListener;
import newcity56.entity.Device_Data;
import newcity56.utils.ByteUtils;
import newcity56.utils.Map2JsonUtils;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by dxy
 * 订单签收
 */

public class OrderSignHelper implements City56Helper {
    String TAG = "OrderbindHelper";
    private Context c;
    private OnSignListener onBindResultListener;
    private static final String seruiecg = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String t11111111111 = "0000ffe1-0000-1000-8000-00805f9b34fb";
    BleManager bleManager;
    private BluetoothAdapter mBluetoothAdapter = null;
    private int TIMEOUT = 15;
    private int commStatus = 0;//0-等待握手1-握手返回，等待确认2-请求数据，等待返回3-请求阈值等待返回
    private String bleMac = "123456";
    public int PACKAGELEN = 20;
    private int ErroCode100 = 100;// 输入蓝牙sn错误
    private int ErroCode101 = 101;//未发现蓝牙设备
    private int ErroCode102 = 102;//service = null连接失败...稍后再试
    private int ErroCode103 = 103;//超时
    private int ErroCode104 = 104;//连接中断
    private int ErroCode105 = 105;//温度计时差相差五分钟
    private int ErroCode106 = 106;//温度计返回数据错误，请重新连接
    private int ErroCode107 = 107;//连接错误
    private int ErroCode108 = 108;//不允许重复绑定运单
    private int ErroCode109 = 109;//预签收失败
    public static final int BLE_READ_TIMEOUT = 45;
    private int packNo;
    private Handler mHandler;
    private Date orderSignDate, boxBeginDate;
    int dataIndex;//数据索引
    private boolean firstPackage;
    private StringBuilder stringBuilder = new StringBuilder();
    private String cmdOrder;//下发运单号命令文本，转换后发送给设备蓝牙的运单号，最大支持20位，转换为ascii后40位长，右补0
    private String account;
    public static final int ACCOUNTLEN = 16;
    public static final int TRANSPORTLEN = 20;
    private List<Device_Data> mSignResultData = new ArrayList<>();
    private String num;

    public OrderSignHelper(Context c, OnSignListener onBindResultListener) {
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
    public void cnDeviceBle(final String searchCode, final String num, int dataIndex, String login, Date orderSignDate, Date boxSignDate) {
        if (TextUtils.isEmpty(searchCode) || TextUtils.isEmpty(login) || TextUtils.isEmpty(num) || orderSignDate == null || boxSignDate == null) {
            onBindResultListener.onFail(666);
            return;
        }
        onBindResultListener.onBegin();
        this.num = num;
        this.dataIndex = dataIndex;
        this.orderSignDate = orderSignDate;
        this.boxBeginDate = boxSignDate;
        cmdOrder = genCmdOrder();
        account = genAccout(login);
        mSignResultData.clear();
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
                                BluetoothGattService service = gatt.getService(UUID.fromString(seruiecg));
                                if (service == null) {
                                    bleManager.closeBluetoothGatt();
                                    onBindResultListener.onFail(ErroCode102);
//                                    cnDeviceBle(searchCode);//
                                    return;
                                } else {
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

    /**
     * 蓝牙通信流程
     *
     * @param cmdStatus
     */
    void communicate(final int cmdStatus, final boolean cleanBuffer) {
        Observable.timer(1, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).
                observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                commStatus = cmdStatus;
                if (cleanBuffer) stringBuilder.setLength(0);
                final String deviceMac = bleMac;
                switch (cmdStatus) {
                    case 0://等待接收握手第一帧
                    {
                        Log.d(TAG, "开始监听AA77" + System.currentTimeMillis());
                        bleManager.notify(seruiecg, t11111111111, bleCallback);
                    }
                    break;
                    case 1://发送握手确认帧
                    {
                        bleManager.writeDevice(seruiecg, t11111111111, ByteUtils.cmdString2Bytes("CC9903" + deviceMac + "02EF", true), bleCallback);
                    }
                    break;
                    case 2: {//等待接收握手第二帧
                        bleManager.notify(seruiecg, t11111111111, bleCallback);
                    }
                    break;
                    case 3://发送AA55的61命令，下发预签收
                    {
                        String cmd = genCmd61String();
                        int endPos = (cmd.length() > (packNo + 1) * PACKAGELEN * 2) ? (packNo + 1) * PACKAGELEN * 2 : cmd.length();
                        String send = cmd.substring(packNo * PACKAGELEN * 2, endPos);
                        bleManager.writeDevice(seruiecg, t11111111111, ByteUtils.hexString2Bytes(send), bleCallback);
                    }
                    break;
                    case 4: {//等待预签收结果
                        Log.d(TAG, "wait for pre-sign...");
                        bleManager.notify(seruiecg, t11111111111, bleCallback);
                    }
                    break;
                    case 5://发送AA55的52命令，获取数据
                    {
                        bleManager.writeDevice(seruiecg, t11111111111, ByteUtils.cmdString2Bytes("AA5504" + deviceMac + "5202EF", true), bleCallback);

                    }
                    break;
                    case 6: {//等待接收数据
                        Log.d(TAG, "wait for receive data...");
                        bleManager.notify(seruiecg, t11111111111, bleCallback);
                    }
                    break;
                    case 7://发送接收数据的CC66确认
                    {
                        bleManager.writeDevice(seruiecg, t11111111111, ByteUtils.cmdString2Bytes("cc66010102EF", true), bleCallback);

                    }
                    break;
                    case 8://发送AA55的60命令，下发签收
                    {
                        String cmd = genCmd60String();
                        int endPos = (cmd.length() > (packNo + 1) * PACKAGELEN * 2) ? (packNo + 1) * PACKAGELEN * 2 : cmd.length();
                        String send = cmd.substring(packNo * PACKAGELEN * 2, endPos);
                        bleManager.writeDevice(seruiecg, t11111111111, ByteUtils.hexString2Bytes(send), bleCallback);

                    }
                    break;
                    case 9: {//等待签收结果
                        Log.d(TAG, "wait for 60's result...");
                        bleManager.notify(seruiecg, t11111111111, bleCallback);
                    }
                    break;
                    case 10://发送53断开连接
                    {
                        bleManager.writeDevice(seruiecg, t11111111111, ByteUtils.cmdString2Bytes("AA5504" + deviceMac + "5306ef", true), bleCallback);
                    }
                    break;
                }
                if (cleanBuffer) {
                    Log.d(TAG, "超时启动：" + commStatus + " ti:" + System.currentTimeMillis());
                    mHandler.postDelayed(bleReadTimeout, 1000 * BLE_READ_TIMEOUT);
                }
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
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        bleMac = data.substring(6, 12);
                        Observable.timer(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
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
                        Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                packNo = 0;
                                communicate(3, true);
                            }
                        });
                    }
                }
                break;
                case 3: {//预签收,0x61
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
                            return;
                        }
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        Log.d(TAG, "send aa55 61 success");
                        communicate(4, true);
                    }
                }
                break;
                case 4: {//预签收结果
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
//                                list.get(position111).setPre_sign(1);
//                                MyApplication.getInstance().getDb().saveOrUpdate(list.get(position111));
//                                //删除冗余数据
//                                List<Device_Data> list_data = MyApplication.getInstance().getDb().selector(Device_Data.class)
//                                        .where("transport_info_id", "=", list.get(position111).getTransport_info_id())
//                                        .and("device_code", "=", list.get(position111).getDevice_code())
//                                        .and("data_from", "=", 1).findAll();
//                                if (list_data != null && list_data.size() > 0) {
//                                    MyApplication.getInstance().getDb().delete(list_data);
//                                    deviceDataLatest = getDeviceLatestData(list.get(position111).getDevice_code());
//                                    Log.d(TAG, "清除数据，当前数据最新时间：" + deviceDataLatest.toString());
//                                }
//                            } catch (DbException e) {
//                                e.printStackTrace();
//                            }
                            Observable.timer(200, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                @Override
                                public void call(Long aLong) {
                                    firstPackage = true;
                                    communicate(5, true);
                                }
                            });
                        } else {
                            onBindResultListener.onFail(ErroCode109);
//                            final int showImg = 2;
//                            final String tips = "签收下发失败，请重新尝试";
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    materialDialog.dismiss();
//                                    list.get(position111).setIsshowimage(showImg);
//                                    sendNumForBindAdapter.showPro();
//                                    bleManager.closeBluetoothGatt();
//                                    Toast.makeText(SendNumForSignActivity.this, tips, Toast.LENGTH_LONG).show();
//                                }
//                            });
//                            subscripe = Observable.timer(3, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
//                                @Override
//                                public void call(Long aLong) {
//                                    judgeWhat2Do();
//                                }
//                            });
                        }

                    }
                }
                break;
                case 5: {
                    int start = sReceived.indexOf("aa55");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "send aa55 52 success");
                        Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                communicate(6, true);
                            }
                        });
                    }
                }
                break;
                case 6: {
                    //接收数据，解析
                    int start = sReceived.indexOf("bb55");

                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        Log.d(TAG, "DataLen:" + da.length + " package Len:" + packLen);
                        if (da.length < (packLen + 4) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        mHandler.removeCallbacks(bleReadTimeout);
                        String deviceCode = data.substring(18, 24);
                        int hasData = da[15] & 0xff;
                        if (hasData == 0) {//无数据，直接发签收命令
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    Toast.makeText(SendNumForSignActivity.this, "无签收数据", Toast.LENGTH_LONG).show();
//                                }
//                            });
                            Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                @Override
                                public void call(Long aLong) {
                                    communicate(8, true);
                                }
                            });
                            return;
                        }
                        int itemCount = da[20] & 0xff;
                        boolean needData = true;
                        for (int i = 0; i < itemCount; i++) {
                            Date colDate = Map2JsonUtils.byte2Date(da[16], da[17], da[18], da[19], da[25 + i * 11], da[26 + i * 11]);
                            Date signDate = orderSignDate;
                            if (firstPackage && i == 0) {//判断第一条数据的时间，做保护
                                if ((signDate.getTime() - colDate.getTime()) >= 1000 * 60 * 5) {
                                    Log.d(TAG, "缺少一条数据");
//                                    runOnUiThread(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            materialDialog.dismiss();
//                                            list.get(position111).setIsshowimage(2);
//                                            sendNumForBindAdapter.showPro();
//                                        }
//                                    });
                                    Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                        @Override
                                        public void call(Long aLong) {
                                            communicate(10, true);
                                        }
                                    });
                                    return;
                                }
                            }
                            firstPackage = false;
//                            Log.d(TAG, "数据时间：" + colDate.toString() + "最新数据时间:" + deviceDataLatest.toString());
                            if (colDate.getTime() > signDate.getTime()) {
                                continue;
                            }
                            if (colDate.getTime() > boxBeginDate.getTime()) {//比数据库数据新，写库
                                String t;
                                int ti = da[21 + i * 11] & 0xff;
                                int td = da[22 + i * 11] & 0xff;
                                if (ti > 127) {
                                    ti = ti - 128;
                                    t = "-" + ti;
                                } else {
                                    t = "" + ti;
                                }
                                int dec = td * 625 / 1000;
                                t = t + "." + dec;

                                int power = da[28 + i * 11] & 0xff;
                                int t_alarm = da[29 + i * 11] & 0x0f;
                                Device_Data device_data = new Device_Data();
                                device_data.setTransport_info_id(num);
                                device_data.setCreate_date(Map2JsonUtils.getTime111());
                                device_data.setDevice_code(deviceCode);
                                device_data.setCollect_date(colDate);
                                device_data.setTemp_alarm(t_alarm + "");
                                device_data.setTemperature(t);
                                device_data.setData_from(1);//蓝牙数据
                                device_data.setPower(power + "");
                                device_data.setSerial(Map2JsonUtils.Date2String(colDate, "yyyyMMddHHmm") + deviceCode);
                                mSignResultData.add(device_data);
                            } else {
                                Log.d(TAG, "数据处理完毕");
                                needData = false;
                                break;
                            }
                        }
                        if (needData) {//发送cc66，继续要数据
                            Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                @Override
                                public void call(Long aLong) {
                                    communicate(7, true);
                                }
                            });
                        } else {//数据读取完毕，下发签收命令
                            Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                                @Override
                                public void call(Long aLong) {
                                    onBindResultListener.onDateSucceed(mSignResultData);
                                    packNo = 0;
                                    communicate(8, true);
                                }
                            });
                        }
                    }
                }
                break;
                case 7: {
                    int start = sReceived.indexOf("cc66");
                    if (start >= 0 && sReceived.length() > start + 6) {
                        String data = sReceived.substring(start);
                        byte[] da = ByteUtils.hexString2Bytes(data);
                        int packLen = da[2] & 0xff;
                        if (data.length() < (packLen * 2 + 8) || data.lastIndexOf("ef") < start) {
                            return;
                        }
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "send cc66 success");
                        Observable.timer(300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                communicate(5, true);//继续接收数据
                            }
                        });
                    }
                }
                break;
                case 8: {//发送签收，0x60
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
                                    communicate(8, false);
                                }
                            });
                            return;
                        }
                        mHandler.removeCallbacks(bleReadTimeout);
                        Log.d(TAG, "超时取消：" + commStatus + " ti:" + System.currentTimeMillis());
                        Log.d(TAG, "send aa55 60 success");
                        communicate(9, true);
                    }
                }
                break;
                case 9: {//签收结果
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
//                                list.get(position111).setPre_sign(2);
//                                MyApplication.getInstance().getDb().saveOrUpdate(list.get(position111));
//                            } catch (DbException e) {
//                                e.printStackTrace();
//                            }
                        }
                        final int showImg = (dealOk == 1) ? 3 : 2;
                        final String tips = (dealOk == 1) ? "下发生成PDF指令成功" : "下发生成PDF失败，请重新尝试";
                        bleManager.closeBluetoothGatt();
                        onBindResultListener.onFinaly(tips);
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                materialDialog.dismiss();
//                                list.get(position111).setIsshowimage(showImg);
//                                sendNumForBindAdapter.showPro();
//                                bleManager.closeBluetoothGatt();
//                                Toast.makeText(SendNumForSignActivity.this, tips, Toast.LENGTH_LONG).show();
//                                subscripe = Observable.timer(2, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
//                                    @Override
//                                    public void call(Long aLong) {
//                                        judgeWhat2Do();
//                                    }
//                                });
//                            }
//                        });

                    }
                }
                break;
                case 10: {//断开连接
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
            Log.d(TAG, "超时异常取消：" + commStatus + " ti:" + System.currentTimeMillis());
            mHandler.removeCallbacks(bleReadTimeout);
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


    private static String string2Asc(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            int c = str.charAt(i);
            sb.append(Integer.toHexString(c));
        }
        return sb.toString();
    }

    private String genCmd61String() {
        StringBuilder cmd = new StringBuilder("AA5520");
        cmd.append(bleMac).append("61").append(cmdOrder);
        Date signDate = orderSignDate;
        cmd.append(Map2JsonUtils.date2HexString(signDate));

        int dataIndex;
        long diff = signDate.getTime() - boxBeginDate.getTime();
        if (diff >= 0) {
            long elapseMinute = diff / (1000 * 60 * 5);
            dataIndex = (this.dataIndex + (int) elapseMinute) % 65535;
        } else {
            long elapseMinute = Math.abs(diff) / (1000 * 60 * 5);
            dataIndex = (this.dataIndex - (int) elapseMinute - 1) % 65535;

        }
        StringBuilder diSb = new StringBuilder(Integer.toHexString(dataIndex));
        while (diSb.length() < 4) {
            diSb.insert(0, "0");
        }
        cmd.append(diSb.toString()).append("04EF");
        byte[] bArr = ByteUtils.cmdString2Bytes(cmd.toString(), true);
        return ByteUtils.bytes2HexString(bArr);
    }

    private String genCmd60String() {
        StringBuilder cmd = new StringBuilder("AA5528");
        cmd.append(bleMac).append("60").append(cmdOrder);
        cmd.append(account).append("04EF");
        byte[] bArr = ByteUtils.cmdString2Bytes(cmd.toString(), true);
        return ByteUtils.bytes2HexString(bArr);
//        return cmd.toString();
    }

    private String genCmdOrder() {
        //转换asc字符串，总长度20位，运单号长度不足以0右补齐
        String ascS = string2Asc(num);
        StringBuilder cmd = new StringBuilder(ascS);
        while (cmd.length() < TRANSPORTLEN * 2) {
            cmd.append("0");
        }
        return cmd.toString();
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
