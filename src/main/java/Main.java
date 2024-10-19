import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
// import com.dampcake.bencode.Bencode; - available if you need it!

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    String command = args[0];
      switch (command) {
          case "decode" -> {
              //  Uncomment this block to pass the first stage
              String bencodedValue = args[1];
              Object decoded;
              try {
                  Bencode.preserveStrings();
                  decoded = Bencode.bdecode(
                          new PushbackInputStream(
                                  new ByteArrayInputStream(bencodedValue.getBytes(StandardCharsets.UTF_8))
                          )
                  );
                  Bencode.reset();
              } catch (RuntimeException e) {
                  System.out.println(e.getMessage());
                  return;
              }
              System.out.println(gson.toJson(decoded));
          }
          case "info" -> {
              byte[] torrentFile = Files.readAllBytes(Paths.get(args[1]));
              Map<String, Object> torrentData = parseTorrentInfo(torrentFile);
              String announceUrl = new String((byte[]) torrentData.get("announce"), StandardCharsets.UTF_8);
              Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
              long length = (long) info.get("length");
              long pieceLength = (long) info.get("piece length");
              byte[] pieces = (byte[]) info.get("pieces");
              List<byte[]> splitPiecesList = splitPieces(pieces);
              List<String> splitPiecesHexList = new ArrayList<>(splitPiecesList.size());
              for (byte[] piece : splitPiecesList) {
                  splitPiecesHexList.add(Utils.getHex(piece));
              }
              byte[] infoHash = getInfoHash(info);

              System.out.println("Tracker URL: " + announceUrl);
              System.out.println("Length: " + length);
              System.out.println("Info Hash: " + Utils.calculateSHA1(infoHash));
              System.out.println("Piece Length: " + pieceLength);
              System.out.println("Piece Hashes: " + formatPiecesHex(splitPiecesHexList));
          }
          case "peers" -> {
              byte[] torrentFile = Files.readAllBytes(Paths.get(args[1]));
              Map<String, Object> torrentData = parseTorrentInfo(torrentFile);
              String announceUrl = new String((byte[]) torrentData.get("announce"), StandardCharsets.UTF_8);
              Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
              long length = (long) info.get("length");
              byte[] infoHash = Utils.calculateSHA1Raw(getInfoHash(info));

              discoverPeers(announceUrl, infoHash, length);
          }
          case "handshake" -> {
              handshake(args[1], args[2]);
          }
          case null, default -> System.out.println("Unknown command: " + command);
      }

  }

  private static void handshake(String torrentFile, String hostPort) {
      try {
          byte[] torrentBytes = Files.readAllBytes(Paths.get(torrentFile));
          Map<String, Object> torrentData = parseTorrentInfo(torrentBytes);
          Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
          byte[] infoHash = Utils.calculateSHA1Raw(getInfoHash(info));

          String[] split = hostPort.split(":");
          String host = split[0], port = split[1];

          try (Socket socket = new Socket(host, Integer.parseInt(port))) {
              OutputStream outputStream = socket.getOutputStream();
              ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
              byteArrayOutputStream.write(19);
              byteArrayOutputStream.write("BitTorrent protocol".getBytes(StandardCharsets.UTF_8));
              byteArrayOutputStream.write(new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
              byteArrayOutputStream.write(infoHash);
              byteArrayOutputStream.write("01234567890123456789".getBytes(StandardCharsets.UTF_8));
              outputStream.write(byteArrayOutputStream.toByteArray());

              InputStream inputStream = socket.getInputStream();
              byte[] response = new byte[68]; // 68 is the total size of request sent;
              inputStream.read(response);
              inputStream.close();

              final byte[] peerIdResponse = new byte[20];
              final ByteBuffer buffer = ByteBuffer.wrap(response);
              buffer.position(48);
              buffer.get(peerIdResponse, 0, 20);

              System.out.println("Peer ID: " + Utils.getHex(peerIdResponse));


          }

      } catch (IOException | NoSuchAlgorithmException e) {
          System.out.println("Error when parsing torrent file");
      }
  }

  static private byte[] getInfoHash(Map<String, Object> info) throws IOException {
    ByteArrayOutputStream bencodedInfoOutputStream = new ByteArrayOutputStream();
    Bencode.bencode(info, bencodedInfoOutputStream);
    return bencodedInfoOutputStream.toByteArray();
  }

  static private Map<String, Object> parseTorrentInfo(byte[] torrentFile) throws IOException {
    PushbackInputStream in = new PushbackInputStream(new ByteArrayInputStream(torrentFile));
    return (Map<String, Object>) Bencode.bdecode(in);
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
      Map<String, Object> responseMap = (Map<String, Object>) Bencode.bdecode(new PushbackInputStream(inputStream));
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
}
