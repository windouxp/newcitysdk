package newcity56.utils;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Locale;

public class ByteUtils {

    public static byte[] cmdString2Bytes(String cmd, boolean withSumCode) {
        byte[] value = hexString2Bytes(cmd);
        if (withSumCode) {
            if (value.length<6) return value;
            byte checkSum = value[2];
            for (int i = 3; i <value.length-2 ; i++) {
                checkSum = (byte)(checkSum ^ value[i]);
            }
            value[value.length-2] = checkSum;
            return value;
        } else {
            return value;
        }
    }
    public static byte[] String2Bytes(String hexString) {
        int len = hexString.length() / 2;
        char[] chars = hexString.toCharArray();
        String[] hexStr = new String[len];
        byte[] bytes = new byte[len];
        for (int i = 0, j = 0; j < len; i += 2, j++) {
            hexStr[j] = "" + chars[i] + chars[i + 1];
            bytes[j] = (byte) Integer.parseInt(hexStr[j], 10);
        }
        return bytes;
    }
    public static byte[] Hex2017To2byte(String hexString) {//同步时间用的
        String[] byteStr = new String[2];
        int length = hexString.length();
        int split = length%2==0?(length/2):(length-1)/2;
        byteStr[0]=hexString.substring(0, split);
        byteStr[1]=hexString.substring(split, length);
        byte[] bytes = new byte[2];
        bytes[0] = (byte) Integer.parseInt(byteStr[0], 16);
        bytes[1] = (byte) Integer.parseInt(byteStr[1], 16);
        return bytes;
    }
    public static byte[] reverseBytes(byte[] a) {
        int len = a.length;
        byte[] b = new byte[len];
        for (int k = 0; k < len; k++) {
            b[k] = a[a.length - 1 - k];
        }
        return b;
    }


    public static String bytes2HexString(byte[] bytes) {
        String result = "";
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xff);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            result += hex.toLowerCase(Locale.getDefault());
        }
        return result;
    }


    public static String bytes2HexString(byte[] bytes, int len) {
        String result = "";
        for (int i = 0; i < len; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xff);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            result += hex.toLowerCase(Locale.getDefault());
        }
        return result;
    }

    public static final String bytesToHexString(byte[] bArray, int len) {
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < len; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    public static String bytes2String(byte[] bArray){
        try {
            int length = 0;
            for (int i = 0; i<bArray.length;i++){
                if (bArray[i]==0){
                    length = i;
                    break;
                }
            }
            return new String(bArray, 0, length, "UTF-8");
        } catch (UnsupportedEncodingException e){
            e.printStackTrace();
            return "";
        }


    }


    /*public static byte[] hexString2Bytes(String hexString) {
        int len = hexString.length() / 2;
        char[] chars = hexString.toCharArray();
        String[] hexStr = new String[len];
        byte[] bytes = new byte[len];
        for (int i = 0, j = 0; j < len; i += 2, j++) {
            hexStr[j] = "" + chars[i] + chars[i + 1];
            bytes[j] = (byte) Integer.parseInt(hexStr[j], 16);
        }
        return bytes;
    }*/

    public static byte[] hexString2Bytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }


    public static byte genChecksum(byte[] bArray) {
        byte checkSum = 0x0;
        if (bArray.length<6) return checkSum;
        for (int i = 3; i <bArray.length-2 ; i++) {
            checkSum = (byte)(checkSum ^ bArray[i]);
        }
        return checkSum;
    }

    public static ArrayList<Byte> hexString2List(String hexString) {
        int len = hexString.length() / 2;
        char[] chars = hexString.toCharArray();
        String[] hexStr = new String[len];
        ArrayList<Byte> list = new ArrayList<Byte>();
        for (int i = 0, j = 0; j < len; i += 2, j++) {
            hexStr[j] = "" + chars[i] + chars[i + 1];
            list.add((byte) Integer.parseInt(hexStr[j], 16));
        }
        return list;
    }


    public static byte[] short2Bytes(short s) {
        byte[] buf = new byte[2];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) (s & 0x00ff);
            s >>= 8;
        }
        return buf;
    }



    public static String hexString2binaryString(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0)
            return null;
        String bString = "", tmp;
        for (int i = 0; i < hexString.length(); i++) {
            tmp = "0000"
                    + Integer.toBinaryString(Integer.parseInt(hexString
                    .substring(i, i + 1), 16));
            bString += tmp.substring(tmp.length() - 4);
        }
        return bString;
    }






}
