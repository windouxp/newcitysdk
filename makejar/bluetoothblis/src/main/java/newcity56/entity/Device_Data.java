package newcity56.entity;


import java.util.Date;

/**
 * Created by chenzhuo on 2017/6/14.
 */
public class Device_Data {
    public Device_Data() {

    }

    private int id;
    private String device_code;
    private String transport_info_id;
    private String serial; //数据序列号 yyyyMMddHHmm+code
    private String create_date;
    private Date collect_date;
    private String temperature;
    private String humidity;
    private String temp_alarm;//温度状态
    /*0-正常
    1-高温告警
    2-高温预警
    3-低温预警
    4-低温告警*/
    private String humi_alarm;//湿度状态
    /*0-正常
    1-高湿告警
    2-高湿预警
    3-低湿预警
    4-低湿告警*/
    private String door;
    private String power;

    private int data_from;  //0-从平台下载，1-从设备蓝牙获取

    @Override
    public String toString() {
        return "Device_Data{" +
                "id=" + id +
                ", device_code='" + device_code + '\'' +
                ", transport_info_id='" + transport_info_id + '\'' +
                ", serial='" + serial + '\'' +
                ", create_date=" + create_date +
                ", collect_date=" + collect_date +
                ", temperature='" + temperature + '\'' +
                ", humidity='" + humidity + '\'' +
                ", temp_alarm='" + temp_alarm + '\'' +
                ", humi_alarm='" + humi_alarm + '\'' +
                ", door='" + door + '\'' +
                ", power='" + power + '\'' +
                '}';
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDevice_code() {
        return device_code;
    }

    public void setDevice_code(String device_code) {
        this.device_code = device_code;
    }

    public String getTransport_info_id() {
        return transport_info_id;
    }

    public void setTransport_info_id(String transport_info_id) {
        this.transport_info_id = transport_info_id;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public int getData_from() {
        return data_from;
    }

    public void setData_from(int data_from) {
        this.data_from = data_from;
    }

    public String getCreate_date() {
        return create_date;
    }

    public void setCreate_date(String create_date) {
        this.create_date = create_date;
    }

    public Date getCollect_date() {
        return collect_date;
    }

    public void setCollect_date(Date collect_date) {
        this.collect_date = collect_date;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }

    public String getHumidity() {
        return humidity;
    }

    public void setHumidity(String humidity) {
        this.humidity = humidity;
    }

    public String getTemp_alarm() {
        return temp_alarm;
    }

    public void setTemp_alarm(String temp_alarm) {
        this.temp_alarm = temp_alarm;
    }

    public String getHumi_alarm() {
        return humi_alarm;
    }

    public void setHumi_alarm(String humi_alarm) {
        this.humi_alarm = humi_alarm;
    }

    public String getDoor() {
        return door;
    }

    public void setDoor(String door) {
        this.door = door;
    }

    public String getPower() {
        return power;
    }

    public void setPower(String power) {
        this.power = power;
    }


}
