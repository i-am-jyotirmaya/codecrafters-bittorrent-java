import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
// import com.dampcake.bencode.Bencode; - available if you need it!

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
//    System.out.println("Logs from your program will appear here!");
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
        int endIndex = findCorrespondingEIndexForEncodedList(bencodedString, startIndex);
        String encodedList = bencodedString.substring(startIndex + 1, endIndex);
        if(!encodedList.isEmpty()) {
          decoded.add(decodeBencode(encodedList, true));
        } else {
          decoded.add(Collections.emptyList());
        }
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

  private static int findCorrespondingEIndexForEncodedList(String bencodedString, int startIndex) {
    if (bencodedString.charAt(startIndex) != 'l') {
      return -1;
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
        i = findCorrespondingEIndexForEncodedList(bencodedString, i);
      }

    }

    return -1;
  }
  
}
