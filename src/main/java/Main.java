import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import com.dampcake.bencode.Bencode; - available if you need it!

public class Main {
  private static final Gson gson = new Gson();

  private static final HashMap<EncodedType, String> typeMap = new HashMap<>();

  static {
    typeMap.put(EncodedType.INTEGER, "i");
    typeMap.put(EncodedType.LIST, "l");
    typeMap.put(EncodedType.DICTIONARY, "d");
  }

  public static void main(String[] args) throws Exception {
    String command = args[0];
    if("decode".equals(command)) {
      //  Uncomment this block to pass the first stage
        String bencodedValue = args[1];
        Object decoded;
        try {
          decoded = decodeBencode(bencodedValue);
        } catch(RuntimeException e) {
          System.out.println(e.getMessage());
          return;
        }
        System.out.println(gson.toJson(decoded));

    } else if ("info".equals(command)) {
      byte[] torrentFile = Files.readAllBytes(Paths.get(args[1]));
      PushbackInputStream in = new PushbackInputStream(new ByteArrayInputStream(torrentFile));
      Map<String, Object> torrentData = (Map<String, Object>) Bencode.decodeBencode(in);
      String announceUrl = new String((byte[]) torrentData.get("announce"), StandardCharsets.UTF_8);
      Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
      long length = (long) info.get("length");
      ByteArrayOutputStream bencodedInfoOutputStream = new ByteArrayOutputStream();
      Bencode.bencode(info, bencodedInfoOutputStream);
      System.out.println("Tracker URL: " + announceUrl);
      System.out.println("Length: " + length);
      System.out.println("Info Hash: " + HashingUtils.calculateSHA1(bencodedInfoOutputStream.toByteArray()));
    } else {
      System.out.println("Unknown command: " + command);
    }

  }

  static Object decodeBencode(String bencodedString) {
    return decodeBencode(bencodedString, false);
  }

  static Object decodeBencode(String bencodedString, boolean isProcessingList) {
    int startIndex = 0;
    List<Object> decoded = new ArrayList<>();
    while (startIndex < bencodedString.length()) {
      if (Character.isDigit(bencodedString.charAt(startIndex))) {
        int firstColonIndex = startIndex;
        for(int i = startIndex; i < bencodedString.length(); i++) {
          if(bencodedString.charAt(i) == ':') {
            firstColonIndex = i;
            break;
          }
        }
        int length = Integer.parseInt(bencodedString.substring(startIndex, firstColonIndex));
        String result = bencodedString.substring(firstColonIndex+1, firstColonIndex+1+length);
        decoded.add(result);
        startIndex = firstColonIndex+1+length;
      } else if (bencodedString.charAt(startIndex) == 'i') {
        int endIndex = bencodedString.indexOf('e', startIndex);
        long result = Long.parseLong(bencodedString.substring(startIndex + 1, endIndex));
        decoded.add(result);
        startIndex = endIndex + 1;
      } else if (bencodedString.charAt(startIndex) == 'l') {
        int endIndex = findCorrespondingEnd(bencodedString, startIndex, EncodedType.LIST);
        String encodedList = bencodedString.substring(startIndex + 1, endIndex);
        if(!encodedList.isEmpty()) {
          decoded.add(decodeBencode(encodedList, true));
        } else {
          decoded.add(Collections.emptyList());
        }
        startIndex = endIndex + 1;
      } else if (bencodedString.charAt(startIndex) == 'd') {
        int endIndex = findCorrespondingEnd(bencodedString, startIndex, EncodedType.DICTIONARY);
        String encodedList = bencodedString.substring(startIndex + 1, endIndex);
        List<Object> decodedList = (List<Object>) decodeBencode(encodedList, true);
        HashMap<String, Object> map = new HashMap<>();
        if (!encodedList.isEmpty()) {
          for (int i = 0; i < decodedList.size(); i+=2) {
            map.put((String) decodedList.get(i), decodedList.get(i+1));
          }
        }
        decoded.add(map);
        startIndex = endIndex + 1;
      } else {
        throw new RuntimeException("Only strings are supported at the moment");
      }
    }
    if (decoded.size() > 1 || isProcessingList) {
      return decoded;
    }
    return decoded.getFirst();
  }

  private static int findCorrespondingEnd(String bencodedString, int startIndex, EncodedType type) {
    if (EncodedType.STRING.equals(type)) {
      if (Character.isDigit(bencodedString.charAt(startIndex))) {
        int firstColonIndex = startIndex;
        for (int j = startIndex; j < bencodedString.length(); j++) {
          if (bencodedString.charAt(j) == ':') {
            firstColonIndex = j;
            break;
          }
        }
        int length = Integer.parseInt(bencodedString.substring(startIndex, firstColonIndex));
        return firstColonIndex + length;
      }
    }

    char startChar = typeMap.get(type).charAt(0);

    if (bencodedString.charAt(startIndex) != startChar) {
      return -1;
    }

    if (EncodedType.INTEGER.equals(type)) {
      return bencodedString.indexOf('e', startIndex);
    }

    for (int i = startIndex + 1; i < bencodedString.length(); i++) {
      if(bencodedString.charAt(i) == 'e') {
        return i;
      }
      if (bencodedString.charAt(i) == 'i') {
        i = bencodedString.indexOf('e', i);
      } else if (Character.isDigit(bencodedString.charAt(i))) {
        int firstColonIndex = i;
        for(int j = i; j < bencodedString.length(); j++) {
          if(bencodedString.charAt(j) == ':') {
            firstColonIndex = j;
            break;
          }
        }
        int length = Integer.parseInt(bencodedString.substring(i, firstColonIndex));
        i = firstColonIndex + length;
      } else if (bencodedString.charAt(i) == 'l') {
        i = findCorrespondingEnd(bencodedString, i, EncodedType.LIST);
      } else if (bencodedString.charAt(i) == 'd') {
        i = findCorrespondingEnd(bencodedString, i, EncodedType.DICTIONARY);
      }

    }

    return -1;

  }
  
}
