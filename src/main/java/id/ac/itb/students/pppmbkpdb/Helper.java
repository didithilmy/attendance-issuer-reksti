package id.ac.itb.students.pppmbkpdb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Didit on 4/22/2015.
 */
public class Helper {
    private static ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static byte[] longToBytes(long x) {
        buffer.putLong(0, x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        buffer.put(bytes, 0, bytes.length);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String intArrayToHexString(int[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String a) {
        String s = a.replaceAll(" ", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(s.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    public static byte[] concatBytes(byte[] byte1, byte[] byte2) {
        byte[] res = new byte[byte1.length + byte2.length];
        System.arraycopy(byte1, 0, res, 0, byte1.length);
        System.arraycopy(byte2, 0, res, byte1.length, byte2.length);
        return res;
    }

    public static String byteArrayToNumberString(byte[] input) {
        String val = "";
        for (int i = 0; i < input.length; i++) {
            val = val.concat(String.valueOf(input[i]));
        }
        return val;
    }

    public static byte[] numberStringToByteArray (String input) {
        byte[] buf = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            buf[i] = Byte.valueOf(Character.toString(input.charAt(i)));
        }
        return buf;
    }

    public static byte[] popLastNElement(byte[] array, int lastNElement) {
        byte[] o = new byte[array.length - lastNElement];
        System.arraycopy(array, 0, o, 0, array.length - lastNElement);
        return o;
    }

    public static byte[] popFirstNElement(byte[] array, int firstNElement) {
        if(array.length >= firstNElement) {
            byte[] o = new byte[firstNElement];
            System.arraycopy(array, 0, o, 0, firstNElement);
            return o;
        } else {
            return null;
        }
    }

    public static short bytesToShort(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    public static byte[] shortToBytes(short value) {
        byte[] returnByteArray = new byte[2];
        returnByteArray[1] = (byte) (value & 0xff);
        returnByteArray[0] = (byte) ((value >>> 8) & 0xff);
        return returnByteArray;
    }

    public static int getLastByteIndex(byte[] haystack, byte needle) {
        for(int i=(haystack.length-1); i>=0; i--) {
            if(haystack[i] == needle) {
                return i;
            }
        }
        return haystack.length;
    }

    public static boolean isNumeric(String s) {
        return s.matches("[-+]?\\d*\\.?\\d+");
    }

    public static int roundTo16(int e) {
        if(isMultipleOf16(e)) {
            return e;
        } else {
            return (((e + 16) / 16) * 16);
        }
    }

    public static boolean isMultipleOf16(int e) {
        if(((e/16) * 16) == e) {
            return true;
        } else {
            return false;
        }
    }

    public static int getPaddingIndex(byte[] haystack, byte nullByte, byte paddingByte) {
        for(int i=(haystack.length-1); i>=0; i--) {
            if(haystack[i] == paddingByte || haystack[i] == nullByte) {
                if(haystack[i] == paddingByte) {
                    return i;
                }
            } else {
                return 0;
            }
        }
        return 0;
    }

    public static byte[] composeByteArray(Object... bytes) {
        int arrlength = bytes.length;
        int offset = 0;
        for(int i=0; i<bytes.length; i++) {    // Iterate to find byte array
            Object o = bytes[i];
            if(o instanceof byte[]) {
                arrlength = arrlength + ((byte[]) o).length - 1;    // Subtract with its current position
            } else if(!(o instanceof Byte)) { // Not a suitable input
                return null;
            }
        }

        byte[] arrayOutput = new byte[arrlength];

        for(int x=0; x<bytes.length; x++) {
            Object o = bytes[x];
            if(o instanceof byte[]) {
                System.arraycopy(o, 0, arrayOutput, (x + offset), ((byte[]) o).length);
                offset = offset + ((byte[]) o).length;
            } else if(o instanceof Byte) {
                arrayOutput[x + offset] = (byte) o;
            }
        }

        return arrayOutput;
    }
    /*public static String getLoggingTimestampString() {
        return "[" + String.valueOf(System.currentTimeMillis()) + "] ";
    }*/
}
