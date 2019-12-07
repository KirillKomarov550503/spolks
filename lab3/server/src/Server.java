import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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

  private DatagramSocket socket;
  private volatile List<BufferElement> sendBuffer;
  private volatile List<BufferElement> receiveBuffer;
  private volatile List<ReadSegment> readedMessageIds;
  private static final String DESTINATION_ADDRESS = "127.0.0.1";
  private static final int SOURCE_PORT = 5002;
  private static final int DESTINATION_PORT = 5001;
  private volatile int maxSendBufferSize;
  private static final int TCP_MESSAGE_SIZE = 1043;
  private volatile int dataSize;
  private volatile boolean isNeedStop;
  private volatile String currentCommand;
  private volatile boolean isConnectionOpen;
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7_sem\\spolks\\lab1\\work_directory_for_server\\";
  private volatile FileOutputStream outputStream;
  private static final int BYTE_FOR_READ_WRITE = 1024;
  private volatile int attempts = 0;
  private static final int soTimeout = 20000;
  private volatile int count;
  private volatile List<Client> clients;

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

  private void runFrankenstein() throws InterruptedException, IOException {
    while (isConnectionOpen) {
      for (int iter = 0; iter < clients.size(); iter++) {
        Client client = clients.get(iter);
        String command = client.getCommand();

        String[] words = command.split("\\s");
        switch (words[0].toLowerCase()) {
          case "echo":
            if (!client.isInitialComplete()) {
              String res = executeEcho(words);
              client.setMessage(res.getBytes());
              client.setMessageLength(res.getBytes().length);
              int size = Math.min(res.getBytes().length, 1024);
              client.setSize(size);
              client.setFrom(0);
              client.setTo(size);
              printBitrate();
              client.setInitialComplete(true);
              client.setIndex(1);
              client.setCommandType((byte) 1);
            }
            addToBufferNewElement(client);
            if (client.getMessageLength() == 0) {
              clearBuffer();
              System.out.println("Client: " + client);
              System.out.println("Clients: " + clients);
              clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
            }
            isNeedStop = true;
            break;
          case "time":
            if (!client.isInitialComplete()) {
              String res = executeTime();
              client.setMessage(res.getBytes());
              client.setMessageLength(res.getBytes().length);
              int size = Math.min(res.getBytes().length, 1024);
              client.setSize(size);
              client.setFrom(0);
              client.setTo(size);
              printBitrate();
              client.setInitialComplete(true);
              client.setIndex(1);
              client.setCommandType((byte) 2);
            }
            addToBufferNewElement(client);
            if (client.getMessageLength() == 0) {
              clearBuffer();
              clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
            }
            isNeedStop = true;
            break;
          case "upload":
            currentCommand = words[0].toLowerCase();
//            clearBuffer();
            if (!client.isPresentFileLength()) {
              client.setFileLength(0);
              for (int i = 0; i < receiveBuffer.size(); i++) {
                if (extractClientId(receiveBuffer.get(i).getMessage())
                    .equals(client.getClientId())) {
                  byte[] message = receiveBuffer.get(i).getMessage();
                  if (message[4] == 5) {
                    byte[] messageLengthsBts = Arrays.copyOfRange(message, 15, 19);
                    client.setFileLength(ByteBuffer.wrap(Arrays
                        .copyOfRange(message, 19, 19 + ByteBuffer.wrap(messageLengthsBts).getInt()))
                        .getInt());
                    receiveBuffer.removeIf(elem -> elem.getId() == extractId(message));
                    System.out.println("Received file length: " + client.getFileLength());
                    client.setPresentFileLength(true);
                    break;
                  }
                }
              }
            } else {
              if (!client.isInitialComplete()) {
                int size = Math.min(client.getFileLength(), BYTE_FOR_READ_WRITE);
                client.setSize(size);
                client.setOutputStream(new FileOutputStream(new File(WORK_DIRECTORY_PATH + words[1]), true));
                client.setIndex(3);
                System.out.println("Receive buffer: " + receiveBuffer);
                client.setInitialComplete(true);
              } else {
                printBitrate();
                if (client.getFileLength() != 0 && isConnectionOpen) {
                  if (client.getFileLength() < client.getSize()) {
                    client.setSize(client.getFileLength());
                  }
                  client.setPresentData(false);
                  for (int i = 0; i < receiveBuffer.size(); i++) {
                    if (extractClientId(receiveBuffer.get(i).getMessage())
                        .equals(client.getClientId())) {
                      if (receiveBuffer.get(i).getId() == client.getIndex()
                          && receiveBuffer.get(i).getMessage()[4] == 4) {
                        byte[] messageLengthsBts = Arrays
                            .copyOfRange(receiveBuffer.get(i).getMessage(), 15, 19);
                        byte[] data = Arrays
                            .copyOfRange(receiveBuffer.get(i).getMessage(), 19,
                                19 + ByteBuffer.wrap(messageLengthsBts).getInt());
                        final int j = i;
                        client.getOutputStream().write(data);
                        receiveBuffer
                            .removeIf(
                                elem -> elem.getId() == extractId(
                                    receiveBuffer.get(j).getMessage()));
                        client.setIndex(client.getIndex() + 1);
                        client.setPresentData(true);
                        break;
                      }
                    }
                  }
                  if (!client.isPresentData()) {
                    continue;
                  }
                  client.setFileLength(client.getFileLength() - client.getSize());
                  dataSize += client.getSize();
                }
                if (client.getFileLength() == 0) {
                  client.getOutputStream().close();
                  isNeedStop = true;
                  client.setMessage("File successfully received".getBytes());
                  client.setIndex(1);
                  client.setCommandType((byte) 8);
                  addToBufferNewElement(client);
                  clearBuffer();
                  clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
                }
              }

            }
            isNeedStop = true;
            break;
          case "download":
            if (!client.isInitialComplete()) {
              clearBuffer();
              byte[] file = readFile(WORK_DIRECTORY_PATH + words[1]);
              client.setMessage(ByteBuffer.allocate(4).putInt(file.length).array());
              client.setMessageLength(client.getMessage().length);
              int size = Math.min(client.getMessageLength(), 1024);
              client.setSize(size);
              client.setFrom(0);
              client.setTo(size);
              printBitrate();
              client.setIndex(1);
              client.setCommandType((byte) 5);
              addToBufferNewElement(client);
              client.setMessage(file);
              client.setMessageLength(file.length);
              size = Math.min(client.getMessageLength(), 1024);
              client.setSize(size);
              client.setFrom(0);
              client.setTo(size);
              client.setIndex(2);
              client.setCommandType((byte) 4);
              client.setInitialComplete(true);
            }
            addToBufferNewElement(client);
            if (client.getMessageLength() == 0) {
              clearBuffer();
              client.setMessage("File successfully delivered".getBytes());
              client.setCommandType((byte) 7);
              client.setMessageLength(client.getMessage().length);
              addToBufferNewElement(client);
              clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
              clearBuffer();
            }
            isNeedStop = true;
            break;
          case "exit":
            currentCommand = words[0].toLowerCase();
//            addDataToSendBuffer("Server start closing connection".getBytes(), (byte) 3, 1);
            while (sendBuffer.size() != 0) {
              Thread.sleep(1);
            }
            System.exit(0);
            currentCommand = "";
            break;
        }
        Thread.sleep(1);
      }
    }
    Thread.sleep(1);
  }

  private void addToBufferNewElement(Client client) throws InterruptedException {
    if (sendBuffer.size() < maxSendBufferSize) {
      int from = client.getFrom();
      int messageLength = client.getMessageLength();
      byte[] temp = Arrays.copyOfRange(client.getMessage(), from, client.getTo());
      while (sendBuffer.size() >= maxSendBufferSize) {
        Thread.sleep(1);
      }
      sendBuffer
          .add(new BufferElement(client.getIndex(),
              createMessageHeader(client.getIndex(), client.getCommandType(), temp),
              client.getSocketAddress()));
      from += client.getSize();
      client.setFrom(from);
      messageLength -= client.getSize();
      dataSize += client.getSize();
      client.setMessageLength(messageLength);
      if (messageLength < client.getSize()) {
        client.setSize(messageLength);
      }
      System.out.println("MessageLength: " + messageLength);
      client.setTo(client.getTo() + client.getSize());
      client.setIndex(client.getIndex() + 1);
    }

  }


  private int extractId(byte[] message) {
    return ByteBuffer.wrap(new byte[]{message[0], message[1], message[2], message[3]}).getInt();
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
  private String extractClientId(byte[] message) {
    return new String(Arrays.copyOfRange(message, 5, 15));
  }

  private void receiveMessages() throws IOException, InterruptedException {
    while (true) {
      if (isConnectionOpen) {
        byte[] message = new byte[TCP_MESSAGE_SIZE];
        DatagramPacket receive = new DatagramPacket(message, message.length);
        socket.receive(receive);
        count = 0;
        attempts = 0;
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
            BufferElement elem = new BufferElement(messageId, message, receive.getSocketAddress());
            receiveBuffer.add(elem);

            if (message[4] == 1 || message[4] == 2 || message[4] == 3 || message[4] == 7
                || message[4] == 8) {
              byte[] messageLengthsBts = Arrays.copyOfRange(message, 15, 19);
              byte[] messageBts = Arrays
                  .copyOfRange(message, 19, 19 + ByteBuffer.wrap(messageLengthsBts).getInt());
              String clientId = extractClientId(message);
              System.out.println("Receive command \"" + new String(messageBts) + "\" from client \"" + clientId + "\"");
              receiveBuffer.removeIf(element -> element.getId() == extractId(message));
              clients.add(new Client(clientId, new String(messageBts),
                  receive.getSocketAddress()));
            }

          }
          byte[] emptyData = new byte[1027];
          DatagramPacket ackPacket = new DatagramPacket(
              concat(ByteBuffer.allocate(4).putInt(messageId).array(),
                  concat(new byte[]{6, message[4]}, emptyData)), TCP_MESSAGE_SIZE - 10,
              receive.getSocketAddress());
          socket.send(ackPacket);
        } else {
          //со стороны клиента. Если получили ACK,
          // то удаляем сообщение из буфера отправки.
          final int messageId = extractId(message);
          for (int i = 0; i < sendBuffer.size(); i++) {
            BufferElement element = sendBuffer.get(i);
            if (element.getId() == messageId && element.getMessage()[4] == message[5]) {
              sendBuffer.removeIf(elem -> elem.getId() == messageId);
//                count--;
              break;
            }
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
      byte[] messageLengthsBts = Arrays.copyOfRange(message, 15, 19);
      byte[] messageBts = Arrays
          .copyOfRange(message, 19, 19 + ByteBuffer.wrap(messageLengthsBts).getInt());
      receiveBuffer.removeIf(elem -> elem.getId() == extractId(message));
      return new String(messageBts);
    }
    return new String();
  }

  private void monitorSendBuffer() throws IOException, InterruptedException {
    while (true) {
      if (isConnectionOpen) {
        synchronized (sendBuffer) {
          for (int i = 0; i < sendBuffer.size(); i++) {
            BufferElement element = sendBuffer.get(i);
            if (!element.isWaitAck()) {
              DatagramPacket packet = new DatagramPacket(element.getMessage(),
                  element.getMessage().length, element.getSocketAddress());
              socket.send(packet);
              System.out.println("Packet was send");
              element.waitAck();

            }
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
    clients = Collections.synchronizedList(new ArrayList<>());
    socket = new DatagramSocket(SOURCE_PORT);
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
    Thread detectSoTimeoutThread = new Thread(() -> {
      try {
        detectSocketTimeout();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    });
    sendBufferMonitorThread.start();
    receiveMessagesThread.start();
    detectSoTimeoutThread.start();
  }

  public void closeConnection() {
    socket.close();
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

  private void detectSocketTimeout() throws InterruptedException {
    while (true) {
      if (attempts < 3) {
        if (count >= soTimeout) {
          attempts++;
          count = 0;
          System.err.println("Detect connection issue");
        }
      } else {
        System.err.println("Server detect connection issue 3 times. Stop all commands");
        isConnectionOpen = false;
        isNeedStop = true;
        clearBuffer();
        sendBuffer.clear();
        count = 0;
        currentCommand = "";
        maxSendBufferSize = 3;
        attempts = 0;
        isConnectionOpen = true;
        isConnectionOpen = true;
      }
      if (!currentCommand.isEmpty()) {
        count += 1000;
      }
      Thread.sleep(1000);
    }
  }

//  public void run() throws InterruptedException, IOException {
//    while (!currentCommand.equals("exit")) {
//      String message = read();
//      System.out.println("Receive message: " + message);
//      String[] words = message.split("\\s");
//      switch (words[0].toLowerCase()) {
//        case "echo":
//          currentCommand = words[0].toLowerCase();
//          addDataToSendBuffer(executeEcho(words).getBytes(), (byte) 1, 1);
//          clearBuffer();
//          currentCommand = "";
//          break;
//        case "time":
//          currentCommand = words[0].toLowerCase();
//          addDataToSendBuffer(executeTime().getBytes(), (byte) 2, 1);
//          clearBuffer();
//          currentCommand = "";
//          break;
//        case "upload":
//          currentCommand = words[0].toLowerCase();
//          clearBuffer();
//          receiveFile(WORK_DIRECTORY_PATH + words[1]);
//          addDataToSendBuffer("File successfully received".getBytes(), (byte) 8, 1);
//          clearBuffer();
//          currentCommand = "";
//          break;
//        case "download":
//          currentCommand = words[0].toLowerCase();
//          clearBuffer();
//          int index = sendFile(WORK_DIRECTORY_PATH + words[1]);
//          clearBuffer();
//          addDataToSendBuffer("File successfully delivered".getBytes(), (byte) 7, index);
//          clearBuffer();
//          currentCommand = "";
//          break;
//        case "exit":
//          currentCommand = words[0].toLowerCase();
//          addDataToSendBuffer("Server start closing connection".getBytes(), (byte) 3, 1);
//          while (sendBuffer.size() != 0) {
//            Thread.sleep(1);
//          }
//          System.exit(0);
//          currentCommand = "";
//          break;
//      }
//    }
//  }

  public static void main(String args[]) {
    Server server = new Server();
    try {
      server.init();
      server.runFrankenstein();
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