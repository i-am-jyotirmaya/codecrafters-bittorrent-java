import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {

    private static final String    HEXES    = "0123456789abcdef";

    // Taken from stackoverflow.com https://stackoverflow.com/a/26975031
    static String getHex(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xf0) >> 4)).append(HEXES.charAt((b & 0x0f)));
        }
        return hex.toString();
    }

    public static byte[] calculateSHA1Raw(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(input);
    }

    public static String calculateSHA1(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] messageDigest = md.digest(input);

        // Convert the byte array to a hexadecimal string
        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
