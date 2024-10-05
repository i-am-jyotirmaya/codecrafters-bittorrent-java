import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class Bencode {

    static Object decodeBencode(PushbackInputStream in) throws IOException {
        int indicator = in.read();
        if (indicator == 'i') {
            return decodeInteger(in);
        } else if (indicator == 'l') {
            return decodeList(in);
        } else if (indicator == 'd') {
            return decodeDictionary(in);
        } else if (Character.isDigit(indicator)) {
            return decodeString(in, indicator);
        } else {
            throw new IOException("Invalid bencode format");
        }
    }

    static long decodeInteger(PushbackInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != 'e') {
            sb.append((char) c);
        }
        return Long.parseLong(sb.toString());
    }

    static byte[] decodeString(PushbackInputStream in, int firstDigit) throws IOException {
        StringBuilder lengthStr = new StringBuilder();
        lengthStr.append((char) firstDigit);
        int c;
        while ((c = in.read()) != ':') {
            lengthStr.append((char) c);
        }
        int length = Integer.parseInt(lengthStr.toString());
        byte[] bytes = new byte[length];
        int read = in.read(bytes);
        if (read != length) {
            throw new IOException("Invalid string length");
        }
        return bytes;
    }

    static List<Object> decodeList(PushbackInputStream in) throws IOException {
        List<Object> list = new ArrayList<>();
        while (true) {
            int c = in.read();
            if (c == 'e') {
                break;
            }
            in.unread(c);
            list.add(decodeBencode(in));
        }
        return list;
    }

    static Map<String, Object> decodeDictionary(PushbackInputStream in) throws IOException {
        Map<String, Object> map = new HashMap<>();
        while (true) {
            int c = in.read();
            if (c == 'e') {
                break;
            }
            in.unread(c);
            String key = new String((byte[]) decodeBencode(in), StandardCharsets.UTF_8);
            Object value = decodeBencode(in);
            map.put(key, value);
        }
        return map;
    }


}
