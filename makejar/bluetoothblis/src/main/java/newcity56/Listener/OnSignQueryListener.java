package newcity56.Listener;

import newcity56.entity.TransportDeviceBean;

/**
 * Created by dxy on 2018/1/27.
 */

public interface OnSignQueryListener {
    void onBegin();//初始化
    void onDateSucceed(TransportDeviceBean resultList);//成功
    void onFail(int erroCode);//失败
    void onFinaly();//结束
}
