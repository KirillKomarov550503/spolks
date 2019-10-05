package com.kamarou.spolks.socket;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

public class Server {


  private Socket socket;
  private ServerSocket server;
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7 сем\\spolks\\lab1\\work_directory_for_server\\";
  private static int SIZE = 4;
  private String lastClient = "";
  private String lastCommand = "";
  private String lastFileName;
  private int leftFileLength;
  private static final int BYTES_FOR_READ_WRITE = 500;
  private FileOutputStream outputStream;

  private String readMessage(DataInputStream socketReader) throws IOException {
    String message = socketReader.readUTF();
    StringBuilder builder = new StringBuilder();
    boolean isStart = false;
    for (int i = 0; i < message.length(); i++) {
      if ((byte) message.charAt(i) == -1) {
        isStart = !isStart;
      }
      if (isStart && i > 0) {
        builder.append(message.charAt(i));
      }
    }
    return builder.toString();
  }

  private String executeEcho(String[] request) {
    return Arrays.stream(request).skip(1).collect(Collectors.joining(" "));
  }

  private String executeTime() {
    Date date = new Date();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
    return dateFormat.format(date);
  }

  private void writeInSocket(DataOutputStream socketWrite, String message) throws IOException {
    byte[] resBytes = concatArrays(new byte[]{(byte) -1}, message.getBytes());
    resBytes = concatArrays(resBytes, new byte[]{(byte) -1});
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < resBytes.length; i++) {
      builder.append((char) resBytes[i]);
    }
    socketWrite.writeUTF(builder.toString());
    socketWrite.flush();
  }

  private void closeConnection(Socket socket, DataInputStream socketRead,
      DataOutputStream socketWrite) throws IOException {
    socket.close();
    socketRead.close();
    socketWrite.close();
  }

  private byte[] concatArrays(byte[] array1, byte[] array2) {
    byte[] resultArray = new byte[array1.length + array2.length];
    System.arraycopy(array1, 0, resultArray, 0, array1.length);
    System.arraycopy(array2, 0, resultArray, array1.length, array2.length);
    return resultArray;
  }

  private int readFileLength(DataInputStream socketReader) throws IOException {
    byte[] resultArray = new byte[SIZE];
    int read = socketReader.read(resultArray, 0, SIZE);
    if (read <= -1) {
      System.err.println("Can't read data from socket");
    }
    return ByteBuffer.wrap(resultArray).getInt();
  }

  private void saveFile(DataInputStream socketReader, String fileName, int fileLength)
      throws IOException {
    int size = Math.min(fileLength, BYTES_FOR_READ_WRITE);
    int tempLength = fileLength;
    outputStream = new FileOutputStream(new File(fileName), true);
    Date start = new Date();
    while (fileLength != 0) {
      if (fileLength < size) {
        size = fileLength;
      }
      byte[] partOfFile = new byte[size];
      int status = socketReader.read(partOfFile, 0, size);
      if (status < 0) {
        System.err.println("Problem with reading bytes");
        throw new IOException();
      }
      outputStream.write(partOfFile);
      fileLength -= size;
      leftFileLength = fileLength;
    }
    Date end = new Date();
    long difference = end.getTime() - start.getTime();
    DecimalFormat decimalFormat = new DecimalFormat("#.0#");
    System.out
        .println("Bitrate (b/s): " + decimalFormat.format(tempLength / (difference / 1000.0)));
    outputStream.close();
  }

  private void writeFile(DataOutputStream socketWriter, byte[] file) throws IOException {
    int fileLength = file.length;
    int tempLength = fileLength;
    int size = Math.min(fileLength, BYTES_FOR_READ_WRITE);
    int from = 0;
    int to = from + size;
    Date start = new Date();
    while (fileLength != 0) {
      byte[] temp = Arrays.copyOfRange(file, from, to);
      socketWriter.write(temp);
      socketWriter.flush();
      from += size;
      fileLength -= size;
      if (fileLength < size) {
        size = fileLength;
      }
      to += size;
      leftFileLength = fileLength;
    }
    Date end = new Date();
    long difference = end.getTime() - start.getTime();
    DecimalFormat decimalFormat = new DecimalFormat("#.0#");
    System.out
        .println("Bitrate (b/s): " + decimalFormat.format(tempLength / (difference / 1000.0)));
  }

  private void writeFileLength(DataOutputStream socketWriter, int fileLength) throws IOException {
    byte[] bytes = ByteBuffer.allocate(SIZE).putInt(fileLength).array();
    socketWriter.write(bytes);
    socketWriter.flush();
  }

  private byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }

  private void executeDownloadCommand(DataOutputStream socketWriter, String fileName)
      throws IOException {
    if (!new File(WORK_DIRECTORY_PATH + fileName).exists()) {
      String error = String.format("File with name %s not found", fileName);
      System.err.printf(error);
      writeFileLength(socketWriter, 0);
      writeInSocket(socketWriter, error);
    } else {
      byte[] file = readFile(WORK_DIRECTORY_PATH + fileName);
      writeFileLength(socketWriter, file.length);
      writeFile(socketWriter, file);
      writeInSocket(socketWriter,
          "File " + fileName + " was sucessfully uploaded for client");
      leftFileLength = 0;
      lastFileName = "";
    }
  }

  private void executeUploadCommand(DataInputStream socketReader, DataOutputStream socketWriter,
      String fileName) throws IOException {
    int fileLength = readFileLength(socketReader);
    if (fileLength != 0) {
      saveFile(socketReader, WORK_DIRECTORY_PATH + fileName, fileLength);
      writeInSocket(socketWriter,
          "File " + fileName + " successfully received and saved");
    }
  }

  public void runServer(int port) {
    while (true) {
      try {
        server = new ServerSocket(port);
        server.setSoTimeout(100000);
        System.out.println("Server started");
        System.out.println("Waiting for a client ...");

        socket = server.accept();
        System.out.println("Client accepted");
        DataInputStream socketReader = new DataInputStream(
            new BufferedInputStream(socket.getInputStream()));
        DataOutputStream socketWriter = new DataOutputStream(socket.getOutputStream());
        writeInSocket(socketWriter, "Connection successful");

        String clientAddress = readMessage(socketReader);
        System.out.println("Client address: " + clientAddress);

        writeInSocket(socketWriter, lastClient.equals(clientAddress) ? lastCommand : "");
        if (lastClient.equals(clientAddress)) {
          System.out.printf("%s had time to reconnect", clientAddress);
          if (lastCommand.equals("upload")) {
            writeInSocket(socketWriter, lastFileName);
            writeFileLength(socketWriter, leftFileLength);
            saveFile(socketReader, WORK_DIRECTORY_PATH + lastFileName, leftFileLength);
            writeInSocket(socketWriter,
                "File " + lastFileName + " successfully received and saved");
          }
          if (lastCommand.equals("download")) {
            byte[] file = readFile(WORK_DIRECTORY_PATH + lastFileName);
            writeInSocket(socketWriter, lastFileName);
            int fileLength = readFileLength(socketReader);
            int realLeftFileLength = file.length - fileLength;
            writeFileLength(socketWriter, realLeftFileLength);
            if (fileLength != 0) {
              writeFile(socketWriter, Arrays.copyOfRange(file, fileLength, file.length));
              writeInSocket(socketWriter, "Command download finished");
            }

          }
        }
        lastClient = clientAddress;

        String request = "";
        String command = "";
        while (!command.equals("exit")) {
          request = readMessage(socketReader);
          System.out.println(request);
          String[] words = request.split("\\s");
          command = words[0].toLowerCase();
          lastCommand = command;
          if (!command.isEmpty()) {
            switch (command) {
              case "echo":
                writeInSocket(socketWriter, executeEcho(words));
                break;
              case "time":
                writeInSocket(socketWriter, executeTime());
                break;
              case "exit":
                writeInSocket(socketWriter, "Server start closing connection");
                break;
              case "upload":
                lastFileName = words[1];
                executeUploadCommand(socketReader, socketWriter, words[1]);
                lastFileName = "";
                leftFileLength = 0;
                break;
              case "download":
                lastFileName = words[1];
                executeDownloadCommand(socketWriter, words[1]);
                lastFileName = "";
                leftFileLength = 0;
                break;
              default:
                writeInSocket(socketWriter, "Command not found");
                Thread.sleep(100);
                break;
            }
          }
        }
        System.out.println("Closing connection");
        closeConnection(socket, socketReader, socketWriter);
      } catch (SocketTimeoutException e) {
        System.err.println("Server is tired of waiting client connection");
        try {
          if (socket != null) {
            socket.close();
          }
          if (server != null) {
            server.close();
          }
          if (outputStream != null) {
            outputStream.close();
          }
          lastCommand = "";
          lastClient = "";
          leftFileLength = 0;
          lastFileName = "";
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      } catch (IOException | InterruptedException i) {
        System.err.println("Connection issue");
        System.err.println("LeftFileLength in catch block: " + leftFileLength);
        try {
          if (socket != null) {
            socket.close();
          }
          if (server != null) {
            server.close();
          }
          if (outputStream != null) {
            outputStream.close();
          }
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  public static void main(String args[]) {
    new Server().runServer(5003);
  }
}

