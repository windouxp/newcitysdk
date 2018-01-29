package newcity56.utils;

import com.google.gson.Gson;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * Created by Administrator on 2016/10/20.
 */

public class Map2JsonUtils {
    //接受map对象，返回json字符串

    public static String mapToJson(Map<String, Object> hashMap) {
        Gson gson = new Gson();
//        Map<String,Object> map = new HashMap<String,Object>();
//        map = hashMap;
        return gson.toJson(hashMap);
    }

    ;


    public static String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        return sdf.format(curDate);
    }

    public static String getTime111() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        return sdf.format(curDate);
    }
    public static String Date2String(Date curDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
        return sdf.format(curDate);
    }
    public static String Date2StringFormat(Date curDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(curDate);
    }
    public static String Date2String(Date date, String formatter) {
        SimpleDateFormat sdf = new SimpleDateFormat(formatter);
        return sdf.format(date);
    }
    public static Date String2Date(String str) {
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm");//小写的mm表示的是分钟
        Date date= null;
        try {
            date = sdf.parse(str);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return  date;
    }
    public static Date String2Date(String str, String formatter) {
        SimpleDateFormat sdf = new SimpleDateFormat(formatter);
        Date date= null;
        try {
            date = sdf.parse(str);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return  date;
    }

    public static String date2HexString(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        String year = Integer.toHexString(calendar.get(Calendar.YEAR));
        String month = Integer.toHexString(calendar.get(Calendar.MONTH)+1);
        String day = Integer.toHexString(calendar.get(Calendar.DAY_OF_MONTH));
        String hour = Integer.toHexString(calendar.get(Calendar.HOUR_OF_DAY));
        String minute = Integer.toHexString(calendar.get(Calendar.MINUTE));
        StringBuffer hexString = new StringBuffer();
        if (year.length()<4) hexString.append("0");
        hexString.append(year);
        if (month.length()<2)hexString.append("0");
        hexString.append(month);
        if (day.length()<2)hexString.append("0");
        hexString.append(day);
        if (hour.length()<2)hexString.append("0");
        hexString.append(hour);
        if (minute.length()<2)hexString.append("0");
        hexString.append(minute);
        return hexString.toString();
    }

    /**
     *
     * @param year1
     * @param year2
     * @param month
     * @param day
     * @param hour
     * @param minute
     * @return
     */
    public static Date byte2Date(byte year1,byte year2,byte month, byte day,byte hour, byte minute) {
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//小写的mm表示的是分钟
        int y1 = year1 & 0xff;
        int y2 = year2 & 0xff;
        int year = y1*256+y2;
        int mo = month & 0xff;
        int d = day & 0xff;
        int h = hour & 0xff;
        int mi = minute & 0xff;
        StringBuilder colDate = new StringBuilder();
        colDate.append(String.valueOf(year)).append("-");

        if (month<10) colDate.append("0");
        colDate.append(String.valueOf(mo)).append("-");
        if (day<10) colDate.append("0");
        colDate.append(String.valueOf(d)).append(" ");
        if (hour<10) colDate.append("0");
        colDate.append(String.valueOf(h)).append(":");
        if (minute<10) colDate.append("0");
        colDate.append(String.valueOf(mi)).append(":");
        colDate.append("00");
        Date date= null;
        try {
            date = sdf.parse(colDate.toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return  date;
    }
    public static String byte2DateStr(byte year1,byte year2,byte month, byte day,byte hour, byte minute) {
        int y1 = year1 & 0xff;
        int y2 = year2 & 0xff;
        int year = y1*256+y2;
        int mo = month & 0xff;
        int d = day & 0xff;
        int h = hour & 0xff;
        int mi = minute & 0xff;
        StringBuilder colDate = new StringBuilder();
        colDate.append(String.valueOf(year));
        if (month<10) colDate.append("0");
        colDate.append(String.valueOf(mo));
        if (day<10) colDate.append("0");
        colDate.append(String.valueOf(d));
        if (hour<10) colDate.append("0");
        colDate.append(String.valueOf(h));
        if (minute<10) colDate.append("0");
        colDate.append(String.valueOf(mi));

        return  colDate.toString();
    }
}
