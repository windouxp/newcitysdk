package newcity56.Listener;

/**
 * Created by chenzhuo on 2017/11/6.
 */

public interface OnBindSendResultListener {
    //
    void onBegin();//初始化
    void onSucceed(String succeCode);//成功
    void onFail(int erroCode);//失败
    void onFinaly();//结束
}
