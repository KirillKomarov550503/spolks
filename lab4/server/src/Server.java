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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
  private volatile boolean isConnectionOpen;
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7_sem\\spolks\\lab1\\work_directory_for_server\\";
  private volatile FileOutputStream outputStream;
  private static final int BYTE_FOR_READ_WRITE = 1024;
  private volatile int attempts = 0;
  private static final int soTimeout = 20000;
  private volatile int count;
  private volatile List<Client> clients;
  private ExecutorService threadPoolExecutor;
  private List<Future<Void>> futures;

  private String extractClientId(byte[] message) {
    return new String(Arrays.copyOfRange(message, 5, 15));
  }

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

  private byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }

  private int addDataToSendBuffer(byte[] message, byte commandType, int index, Client client)
      throws InterruptedException {
    int messageLength = message.length;
    int size = Math.min(messageLength, 1024);
    int from = 0;
    int to = from + size;
    while (messageLength != 0 && isConnectionOpen) {
      if (sendBuffer.size() < maxSendBufferSize) {

        byte[] temp = Arrays.copyOfRange(message, from, to);
        while (sendBuffer.size() >= maxSendBufferSize) {
          Thread.sleep(1);
        }
        sendBuffer.add(new BufferElement(index, createMessageHeader(index, commandType, temp),
            client.getSocketAddress()));
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
    return index;
  }

  private int extractId(byte[] message) {
    return ByteBuffer.wrap(new byte[]{message[0], message[1], message[2], message[3]}).getInt();
  }

  private void receiveFile(String filePath, Client client)
      throws IOException, InterruptedException {
    boolean isPresentFileLength = false;
    int fileLength = 0;
    while (!isPresentFileLength && isConnectionOpen) {
      for (int i = 0; i < receiveBuffer.size(); i++) {
        if (extractClientId(receiveBuffer.get(i).getMessage())
            .equals(client.getClientId())) {
          byte[] message = receiveBuffer.get(i).getMessage();
          if (message[4] == 5) {
            isPresentFileLength = true;
            byte[] messageLengthsBts = Arrays.copyOfRange(message, 15, 19);
            fileLength = ByteBuffer.wrap(Arrays
                .copyOfRange(message, 19, 19 + ByteBuffer.wrap(messageLengthsBts).getInt()))
                .getInt();
            receiveBuffer.removeIf(elem -> elem.getId() == extractId(message));
            break;
          }
        }

      }
    }
    int size = Math.min(fileLength, BYTE_FOR_READ_WRITE);
    outputStream = new FileOutputStream(new File(filePath), true);
    int index = 3;
    while (fileLength != 0 && isConnectionOpen) {
      if (fileLength < size) {
        size = fileLength;
      }
      boolean isPresentData = false;
      for (int i = 0; i < receiveBuffer.size(); i++) {
        if (extractClientId(receiveBuffer.get(i).getMessage())
            .equals(client.getClientId())) {
          if (receiveBuffer.get(i).getId() == index && receiveBuffer.get(i).getMessage()[4] == 4) {
            byte[] messageLengthsBts = Arrays
                .copyOfRange(receiveBuffer.get(i).getMessage(), 15, 19);
            byte[] data = Arrays
                .copyOfRange(receiveBuffer.get(i).getMessage(), 19,
                    19 + ByteBuffer.wrap(messageLengthsBts).getInt());
            final int j = i;
            outputStream.write(data);
            receiveBuffer
                .removeIf(elem -> elem.getId() == extractId(receiveBuffer.get(j).getMessage()));
            index++;
            isPresentData = true;
            break;
          }
        }

      }
      if (!isPresentData) {
        continue;
      }
      fileLength -= size;
      dataSize += size;
    }
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
  private int sendFile(String filePath, Client client) throws InterruptedException, IOException {
    byte[] file = readFile(filePath);
    addDataToSendBuffer(ByteBuffer.allocate(4).putInt(file.length).array(), (byte) 5, 1, client);
    return addDataToSendBuffer(file, (byte) 4, 2, client);
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
                && readedMessageIds.get(i).getType() == message[4]
                && readedMessageIds.get(i).getClientId().equals(extractClientId(message))) {
              isContain = true;
              break;
            }
          }
          if (!isContain) {
            BufferElement elem = new BufferElement(messageId, message, receive.getSocketAddress());
            receiveBuffer.add(elem);
            readedMessageIds.add(new ReadSegment(messageId, message[4], extractClientId(message)));

            if (message[4] == 1 || message[4] == 2 || message[4] == 3 || message[4] == 7
                || message[4] == 8) {
              byte[] messageLengthsBts = Arrays.copyOfRange(message, 15, 19);
              byte[] messageBts = Arrays
                  .copyOfRange(message, 19, 19 + ByteBuffer.wrap(messageLengthsBts).getInt());
              String clientId = extractClientId(message);
              System.out.println(
                  "Receive command \"" + new String(messageBts) + "\" from client \"" + clientId
                      + "\"");
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
              break;
            }
          }
        }
      }
      Thread.sleep(1);
    }
  }

  private void clearBuffer(Client client) {
    receiveBuffer.removeIf(
        bufferElement -> extractClientId(bufferElement.getMessage()).equals(client.getClientId()));
    readedMessageIds
        .removeIf(readSegment -> readSegment.getClientId().equals(client.getClientId()));
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
    socket = new DatagramSocket(SOURCE_PORT);
    maxSendBufferSize = 4;
    isConnectionOpen = true;
    threadPoolExecutor = Executors.newFixedThreadPool(1);
    clients = new CopyOnWriteArrayList<>();
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
        readedMessageIds.clear();
        receiveBuffer.clear();
        sendBuffer.clear();
        count = 0;
        maxSendBufferSize = 3;
        attempts = 0;
        isConnectionOpen = true;
        isConnectionOpen = true;
      }
      if (clients.size() > 0) {
        count += 1000;
      }
      Thread.sleep(1000);
    }

  }

  public void run() throws InterruptedException, IOException {
    while (true) {
      for (int i = 0; i < clients.size(); i++) {

        Client client =  clients.get(i);
        if (!client.isStartExecute()) {
          client.setStartExecute(true);
          threadPoolExecutor.execute(() -> {
            try {
              String message = client.getCommand();
              String[] words = message.split("\\s");
              switch (words[0].toLowerCase()) {
                case "echo":
                  addDataToSendBuffer(executeEcho(words).getBytes(), (byte) 1, 1, client);
                  Thread.sleep(100);
                  clearBuffer(client);
                  client.setStartExecute(true);
                  clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
                  break;
                case "time":
                  addDataToSendBuffer(executeTime().getBytes(), (byte) 2, 1, client);
                  Thread.sleep(100);
                  clearBuffer(client);
                  client.setStartExecute(true);
                  clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
                  break;
                case "upload":
                  clearBuffer(client);
                  receiveFile(WORK_DIRECTORY_PATH + words[1], client);
                  addDataToSendBuffer("File successfully received".getBytes(), (byte) 8, 1, client);
                  Thread.sleep(100);
                  clearBuffer(client);
                  client.setStartExecute(true);
                  clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
                  break;
                case "download":
                  clearBuffer(client);
                  int index = sendFile(WORK_DIRECTORY_PATH + words[1], client);
                  Thread.sleep(100);
                  clearBuffer(client);
                  addDataToSendBuffer("File successfully delivered".getBytes(), (byte) 7, index,
                      client);
                  Thread.sleep(100);
                  clearBuffer(client);
                  client.setStartExecute(true);
                  clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
                  break;
                case "exit":
                  addDataToSendBuffer("Server start closing connection".getBytes(), (byte) 3, 1,
                      client);
                  while (sendBuffer.size() != 0) {
                    Thread.sleep(1);
                  }
                  clients.removeIf(cl -> cl.getClientId().equals(client.getClientId()));
                  System.exit(0);
                  break;
              }
            } catch (InterruptedException | IOException e) {
              e.printStackTrace();
            }
          });
        }
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