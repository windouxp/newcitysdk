package newcity56.entity;

import java.util.Date;

/**
 * Created by wangyong on 2017/12/29.
 */

public class TransportDeviceBean {
    private String code;
    private String crruDate;
    private String power;
    private String door;
    private String threshold;
    private String address;
    private String alarm;
    private String temperature;
    private String lowTem;//阀值低温
    private String higTem;//阀值高温
    private int bindDataIndex;//绑定索引值
    private int signDataIndex;//签收索引值
    private Date bindDate;
    private Date signDate;
    private int cnSuccess;
    private int orderStat;
    private String bindNum;

    @Override
    public String toString() {
        return "TransportDeviceBean{" +
                "code='" + code + '\'' +
                ", crruDate='" + crruDate + '\'' +
                ", power='" + power + '\'' +
                ", door='" + door + '\'' +
                ", threshold='" + threshold + '\'' +
                ", address='" + address + '\'' +
                ", alarm='" + alarm + '\'' +
                ", temperature='" + temperature + '\'' +
                ", lowTem='" + lowTem + '\'' +
                ", higTem='" + higTem + '\'' +
                ", bindDataIndex=" + bindDataIndex +
                ", signDataIndex=" + signDataIndex +
                ", bindDate=" + bindDate +
                ", signDate=" + signDate +
                ", cnSuccess=" + cnSuccess +
                ", orderStat=" + orderStat +
                ", bindNum='" + bindNum + '\'' +
                '}';
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCrruDate() {
        return crruDate;
    }

    public void setCrruDate(String crruDate) {
        this.crruDate = crruDate;
    }

    public String getPower() {
        return power;
    }

    public void setPower(String power) {
        this.power = power;
    }

    public String getDoor() {
        return door;
    }

    public void setDoor(String door) {
        this.door = door;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAlarm() {
        return alarm;
    }

    public void setAlarm(String alarm) {
        this.alarm = alarm;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }

    public String getLowTem() {
        return lowTem;
    }

    public void setLowTem(String lowTem) {
        this.lowTem = lowTem;
    }

    public String getHigTem() {
        return higTem;
    }

    public void setHigTem(String higTem) {
        this.higTem = higTem;
    }

    public int getBindDataIndex() {
        return bindDataIndex;
    }

    public void setBindDataIndex(int bindDataIndex) {
        this.bindDataIndex = bindDataIndex;
    }

    public int getSignDataIndex() {
        return signDataIndex;
    }

    public void setSignDataIndex(int signDataIndex) {
        this.signDataIndex = signDataIndex;
    }

    public Date getBindDate() {
        return bindDate;
    }

    public void setBindDate(Date bindDate) {
        this.bindDate = bindDate;
    }

    public Date getSignDate() {
        return signDate;
    }

    public void setSignDate(Date signDate) {
        this.signDate = signDate;
    }

    public int getCnSuccess() { return cnSuccess; }

    public void setCnSuccess(int cnSuccess) {
        this.cnSuccess = cnSuccess;
    }

    public int getOrderStat() { return orderStat; }

    public void setOrderStat(int orderStat) { this.orderStat = orderStat; }

    public String getBindNum() { return bindNum; }

    public void setBindNum(String bindNum) { this.bindNum = bindNum; }
}
