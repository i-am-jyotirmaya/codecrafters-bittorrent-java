import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
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
      Map<String, Object> torrentData = parseTorrentInfo(torrentFile);
      String announceUrl = new String((byte[]) torrentData.get("announce"), StandardCharsets.UTF_8);
      Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
      long length = (long) info.get("length");
      long pieceLength = (long) info.get("piece length");
      byte[] pieces = (byte[]) info.get("pieces");
      List<byte[]> splitPiecesList = splitPieces(pieces);
      List<String> splitPiecesHexList = new ArrayList<>(splitPiecesList.size());
      for(byte[] piece: splitPiecesList) {
        splitPiecesHexList.add(Utils.getHex(piece));
      }
      byte[] infoHash = getInfoHash(info);

      System.out.println("Tracker URL: " + announceUrl);
      System.out.println("Length: " + length);
      System.out.println("Info Hash: " + Utils.calculateSHA1(infoHash));
      System.out.println("Piece Length: " + pieceLength);
      System.out.println("Piece Hashes: " + formatPiecesHex(splitPiecesHexList));
    } else if ("peers".equals(command)) {
      byte[] torrentFile = Files.readAllBytes(Paths.get(args[1]));
      Map<String, Object> torrentData = parseTorrentInfo(torrentFile);
      String announceUrl = new String((byte[]) torrentData.get("announce"), StandardCharsets.UTF_8);
      Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
      long length = (long) info.get("length");
      byte[] infoHash = Utils.calculateSHA1Raw(getInfoHash(info));

      discoverPeers(announceUrl, infoHash, length);
    } else {
      System.out.println("Unknown command: " + command);
    }

  }

  static private byte[] getInfoHash(Map<String, Object> info) throws IOException {
    ByteArrayOutputStream bencodedInfoOutputStream = new ByteArrayOutputStream();
    Bencode.bencode(info, bencodedInfoOutputStream);
    return bencodedInfoOutputStream.toByteArray();
  }

  static private Map<String, Object> parseTorrentInfo(byte[] torrentFile) throws IOException {
    PushbackInputStream in = new PushbackInputStream(new ByteArrayInputStream(torrentFile));
    return (Map<String, Object>) Bencode.decodeBencode(in);
  }
  static private void discoverPeers(String trackerUrl, byte[] infoHash, long fileLength) throws UnsupportedEncodingException {
    String urlEncodedInfoHash =
            URLEncoder.encode(new String(infoHash, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
    discoverPeers(trackerUrl, urlEncodedInfoHash, fileLength);
  }
  static private void discoverPeers(String trackerUrl, String infoHash, long fileLength) {

    StringBuilder url = new StringBuilder(trackerUrl);
    url.append('?')
            .append("info_hash=")
            .append(infoHash)
            .append("&peer_id=")
            .append("qwertyuiopqwertyuiop")
            .append("&port=")
            .append(6881)
            .append("&uploaded=")
            .append(0)
            .append("&downloaded=")
            .append(0)
            .append("&left=")
            .append(fileLength)
            .append("&compact=")
            .append(1);
    try {
      OkHttpClient client = new OkHttpClient();
      Request request = new Request.Builder()
              .url(url.toString())
              .build();
      InputStream inputStream = null;
      try (Response response = client.newCall(request).execute()) {
        ResponseBody body = response.body();
          assert body != null;
          inputStream = new ByteArrayInputStream(body.byteStream().readAllBytes());
      } catch (IOException ioException) {
        throw new RuntimeException(ioException);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = (Map<String, Object>) Bencode.decodeBencode(new PushbackInputStream(inputStream));
      byte[] peers = (byte[]) responseMap.get("peers");
      for(int i = 0; i < peers.length; i += 6) {
        byte[] peer = new byte[4];
        byte[] peerPort = new byte[2];
        System.arraycopy(peers, i, peer, 0, 4);
        System.arraycopy(peers, i + 4, peerPort, 0, 2);
        int port = ((peerPort[0] & 0xff) << 8) | (peerPort[1] & 0xff);
        InetAddress ia = InetAddress.getByAddress(peer);
        InetSocketAddress isa = new InetSocketAddress(ia, port);
        System.out.println(isa.getAddress().getHostAddress() + ":" +
                isa.getPort());
      }


//      HttpResponse response = client.send(request, )

    } catch (Exception e) {

    }

  }

  static private String formatPiecesHex(List<String> piecesHexList) {
    StringBuilder sb = new StringBuilder();
    for(String pieceHex: piecesHexList) {
      sb.append('\n');
      sb.append(pieceHex);
    }
    return sb.toString();
  }

  static private List<byte[]> splitPieces(byte[] pieces) {
    List<byte[]> splitPieces = new ArrayList<>();
    byte[] piece = new byte[20];
    for(int i = 0; i < pieces.length; i++) {
      if (i % 20 == 0) {
        piece = new byte[20];
        splitPieces.add(piece);
      }

      piece[i % 20] = pieces[i];
    }

    return splitPieces;
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
