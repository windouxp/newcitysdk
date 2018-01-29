package newcity56.Listener;

import newcity56.entity.TransportDeviceBean;

/**
 * Created by chenzhuo on 2017/11/6.
 */

public interface OnBindResultListener {
    //
    void onBegin();//初始化
    void onSucceed(TransportDeviceBean result);//成功
    void onFail(int erroCode);//失败
    void onFinaly();//结束
}
