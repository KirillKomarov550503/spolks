import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Server {

  private DatagramSocket sendSocket;
  private DatagramSocket receiveSocket;
  private volatile List<BufferElement> sendBuffer;
  private volatile List<BufferElement> receiveBuffer;
  private volatile List<ReadSegment> readedMessageIds;
  private static final String DESTINATION_ADDRESS = "127.0.0.1";
  private static final int SOURCE_PORT = 5002;
  private static final int DESTINATION_PORT = 5001;
  private volatile int maxSendBufferSize;
  private static final int TCP_MESSAGE_SIZE = 1033;
  private volatile int dataSize;
  private volatile boolean isNeedStop;
  private volatile String currentCommand;
  private volatile boolean isConnectionOpen;
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7_sem\\spolks\\lab1\\work_directory_for_server\\";
  private volatile FileOutputStream outputStream;
  private static final int BYTE_FOR_READ_WRITE = 1024;

  private byte[] concat(byte[] array1, byte[] array2) {
    byte[] result = new byte[array1.length + array2.length];
    System.arraycopy(array1, 0, result, 0, array1.length);
    System.arraycopy(array2, 0, result, array1.length, array2.length);
    return result;
  }

  private byte[] createMessageHeader(int index, byte messageType, byte[] message) {
    byte[] indexBts = ByteBuffer.allocate(4).putInt(index).array();
    byte[] lengthBts = ByteBuffer.allocate(4).putInt(message.length).array();
    return concat(concat(indexBts, new byte[]{messageType}), concat(lengthBts, message));
  }

  private void printBitrate() {
    isNeedStop = false;
    dataSize = 0;
    Thread thread = new Thread(() -> {
      while (!isNeedStop) {
        dataSize = 0;
        try {
          Thread.sleep(1000);
          DecimalFormat decimalFormat = new DecimalFormat("#.0#");
          System.out.println(
              String.format("Bitrate %s (KB/s)", (decimalFormat.format(dataSize / 1000.0))));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      }
    });
    thread.start();
  }

  private byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }

  private int addDataToSendBuffer(byte[] message, byte commandType, int index)
      throws InterruptedException {
    int messageLength = message.length;
    int size = Math.min(messageLength, 1024);
    int from = 0;
    int to = from + size;
    printBitrate();
//    count = 0;
    while (messageLength != 0) {
      if (sendBuffer.size() < maxSendBufferSize) {

        byte[] temp = Arrays.copyOfRange(message, from, to);
        while (sendBuffer.size() >= maxSendBufferSize) {
          Thread.sleep(1);
        }
        sendBuffer.add(new BufferElement(index, createMessageHeader(index, commandType, temp)));
        from += size;
        messageLength -= size;
        dataSize += size;
        if (messageLength < size) {
          size = messageLength;
        }
        to += size;
        index++;
      }
    }
    isNeedStop = true;
    return index;
  }

  private int extractId(byte[] message) {
    return ByteBuffer.wrap(new byte[]{message[0], message[1], message[2], message[3]}).getInt();
  }

  private void receiveFile(String filePath)
      throws IOException, InterruptedException {
    boolean isPresentFileLength = false;
    int fileLength = 0;
    while (!isPresentFileLength) {
      for (int i = 0; i < receiveBuffer.size(); i++) {
        byte[] message = receiveBuffer.get(i).getMessage();
        if (message[4] == 5) {
          isPresentFileLength = true;
          byte[] messageLengthsBts = Arrays.copyOfRange(message, 5, 9);
          fileLength = ByteBuffer.wrap(Arrays
              .copyOfRange(message, 9, 9 + ByteBuffer.wrap(messageLengthsBts).getInt())).getInt();
          receiveBuffer.removeIf(elem -> elem.getId() == extractId(message));
          break;
        }
      }
    }
    int size = Math.min(fileLength, BYTE_FOR_READ_WRITE);
    outputStream = new FileOutputStream(new File(filePath), true);
    printBitrate();
    int index = 3;
    while (fileLength != 0) {
      if (fileLength < size) {
        size = fileLength;
      }
      boolean isPresentData = false;
      for (int i = 0; i < receiveBuffer.size(); i++) {
        if (receiveBuffer.get(i).getId() == index && receiveBuffer.get(i).getMessage()[4] == 4) {
          byte[] messageLengthsBts = Arrays.copyOfRange(receiveBuffer.get(i).getMessage(), 5, 9);
          byte[] data = Arrays
              .copyOfRange(receiveBuffer.get(i).getMessage(), 9,
                  9 + ByteBuffer.wrap(messageLengthsBts).getInt());
          final int j = i;
          outputStream.write(data);
          receiveBuffer
              .removeIf(elem -> elem.getId() == extractId(receiveBuffer.get(j).getMessage()));
          index++;
          isPresentData = true;
          break;
        }
      }
      if (!isPresentData) {
        continue;
      }
      fileLength -= size;
      dataSize += size;
    }
    isNeedStop = true;
    outputStream.close();
  }

   /*
  биты 0-3 айди пакета, 4 бит - тип пакета, 5-8 размер полезного сообщения, 9 - само полезное сообщение


   */

  /*
   *  1 - echo
   *  2 - time
   *  3 - exit
   *  4 - data
   *  5 - file length
   *  6 - ACK
   *  7 - download
   *  8 - upload
   */
  private int sendFile(String filePath) throws InterruptedException, IOException {
    byte[] file = readFile(filePath);
    addDataToSendBuffer(ByteBuffer.allocate(4).putInt(file.length).array(), (byte) 5, 1);
    return addDataToSendBuffer(file, (byte) 4, 2);
  }


  private void receiveMessages() throws IOException, InterruptedException {
    while (isConnectionOpen) {
      byte[] message = new byte[TCP_MESSAGE_SIZE];
      DatagramPacket receive = new DatagramPacket(message, message.length);
      receiveSocket.receive(receive);
      if (message[4]
          != 6) { // со стороны сервера. Проверяем айдишку уже полученных пакетов. Всегда отправляем ACK при получении пакета
        // Если пакет мы прежде не получали, то добавляем его в буфер чтения и в буфер прочитанных айдишников сообщений
        int messageId = extractId(message);
        boolean isContain = false;
        for (int i = 0; i < readedMessageIds.size(); i++) {
          if (readedMessageIds.get(i).getMessageId() == messageId
              && readedMessageIds.get(i).getType() == message[4]) {
            isContain = true;
            break;
          }
        }
        if (!isContain) {
          BufferElement elem = new BufferElement(messageId, message);
          receiveBuffer.add(elem);
          readedMessageIds.add(new ReadSegment(messageId, message[4]));

        }
        byte[] emptyData = new byte[1027];
        DatagramPacket ackPacket = new DatagramPacket(
            concat(ByteBuffer.allocate(4).putInt(messageId).array(),
                concat(new byte[]{6, message[4]}, emptyData)), TCP_MESSAGE_SIZE,
            InetAddress.getByName(DESTINATION_ADDRESS), DESTINATION_PORT);
        sendSocket.send(ackPacket);
      } else {
        //со стороны клиента. Если получили ACK,
        // то удаляем сообщение из буфера отправки.
        final int messageId = extractId(message);
        for (int i = 0; i < sendBuffer.size(); i++) {
          BufferElement element = sendBuffer.get(i);
          if (element.getId() == messageId && element.getMessage()[4] == message[5]) {
            sendBuffer.removeIf(elem -> elem.getId() == messageId);
//            count--;
            break;
          }
        }
      }
      Thread.sleep(1);
    }
  }

  private void clearBuffer() {
    receiveBuffer.clear();
    readedMessageIds.clear();
  }

  private String read() throws InterruptedException {
    while (receiveBuffer.size() == 0) {
      Thread.sleep(1);
    }
    byte[] message = receiveBuffer.get(0).getMessage();
    if (message[4] == 1 || message[4] == 2 || message[4] == 3 || message[4] == 7
        || message[4] == 8) {
      byte[] messageLengthsBts = Arrays.copyOfRange(message, 5, 9);
      byte[] messageBts = Arrays
          .copyOfRange(message, 9, 9 + ByteBuffer.wrap(messageLengthsBts).getInt());
      receiveBuffer.removeIf(elem -> elem.getId() == extractId(message));
      return new String(messageBts);
    }
    return new String();
  }

  private void monitorSendBuffer() throws IOException, InterruptedException {
    while (isConnectionOpen) {
      synchronized (sendBuffer) {
        for (int i = 0; i < sendBuffer.size(); i++) {
          BufferElement element = sendBuffer.get(i);
          if (!element.isWaitAck()) {
            DatagramPacket packet = new DatagramPacket(element.getMessage(),
                element.getMessage().length,
                InetAddress.getByName(DESTINATION_ADDRESS), DESTINATION_PORT);
            sendSocket.send(packet);
            element.waitAck();

          }
        }
      }
      Thread.sleep(1);
    }

  }

  private void init() throws SocketException, UnknownHostException {
    sendBuffer = Collections.synchronizedList(new ArrayList<>());
    receiveBuffer = Collections.synchronizedList(new ArrayList<>());
    readedMessageIds = Collections.synchronizedList(new ArrayList<>());
    sendSocket = new DatagramSocket();
    receiveSocket = new DatagramSocket(SOURCE_PORT);

    currentCommand = "";
    maxSendBufferSize = 4;
    isConnectionOpen = true;
    Thread sendBufferMonitorThread = new Thread(() -> {
      try {
        monitorSendBuffer();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    });
    Thread receiveMessagesThread = new Thread(() -> {
      try {
        receiveMessages();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    });
    sendBufferMonitorThread.start();
    receiveMessagesThread.start();
  }

  public void closeConnection() {
    sendSocket.close();
    receiveSocket.close();
    System.out.println("Close connection");
  }

  private String executeEcho(String[] words) {
    return Arrays.stream(words).skip(1).collect(Collectors.joining(" "));
  }

  private String executeTime() {
    Date date = new Date();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
    return dateFormat.format(date);
  }

  public void run() throws InterruptedException, IOException {
    while (!currentCommand.equals("exit")) {
      String message = read();
      System.out.println("Receive message: " + message);
      String[] words = message.split("\\s");
      switch (words[0].toLowerCase()) {
        case "echo":
          addDataToSendBuffer(executeEcho(words).getBytes(), (byte) 1, 1);
          currentCommand = "echo";
          clearBuffer();
          break;
        case "time":
          addDataToSendBuffer(executeTime().getBytes(), (byte) 2, 1);
          currentCommand = "time";
          clearBuffer();
          break;
        case "upload":
          clearBuffer();
          receiveFile(WORK_DIRECTORY_PATH + words[1]);
          addDataToSendBuffer("File successfully received".getBytes(), (byte) 8, 1);
          currentCommand = "upload";
          clearBuffer();
          break;
        case "download":
          clearBuffer();
          int index = sendFile(WORK_DIRECTORY_PATH + words[1]);
          clearBuffer();
          addDataToSendBuffer("File successfully delivered".getBytes(), (byte) 7, index);
          currentCommand = "download";
          clearBuffer();
          break;
        case "exit":
          addDataToSendBuffer("Server start closing connection".getBytes(), (byte) 3, 1);
          while (sendBuffer.size() != 0) {
            Thread.sleep(1);
          }
          System.exit(0);
          currentCommand = "exit";
          break;
      }
    }
  }

  public static void main(String args[]) {
    Server server = new Server();
    try {
      server.init();
      server.run();
    } catch (SocketException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (UnknownHostException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}