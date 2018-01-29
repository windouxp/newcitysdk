package newcity56.Listener;

import java.util.List;

import newcity56.entity.Device_Data;

/**
 * Created by dxy on 2018/1/27.
 */

public interface OnSignListener {
    void onBegin();//初始化
    void onDateSucceed(List<Device_Data> resultList);//成功
    void onFail(int erroCode);//失败
    void onFinaly(String s);//结束
}
