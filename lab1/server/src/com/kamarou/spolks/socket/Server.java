package com.kamarou.spolks.socket;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

public class Server {


  private Socket socket;
  private ServerSocket server;
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7 сем\\spolks\\lab1\\work_directory_for_server\\";
  private static int SIZE = 5;

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

  private String insertZeros(String binNum) {
    StringBuilder builder = new StringBuilder(binNum);
    int diff = 8 - binNum.length() - 1;
    for (int i = 0; i < diff; i++) {
      builder.insert(0, '0');
    }
    return builder.toString();
  }

  private byte[] concatArrays(byte[] array1, byte[] array2) {
    byte[] resultArray = new byte[array1.length + array2.length];
    System.arraycopy(array1, 0, resultArray, 0, array1.length);
    System.arraycopy(array2, 0, resultArray, array1.length, array2.length);
    return resultArray;
  }

  private int convertBytesToInt(byte[] bytes) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] >= 0) {
        String binNum = Integer.toBinaryString(bytes[i]);
        if (i != bytes.length - 1 && bytes[i + 1] != -1) {
          stringBuilder.append(insertZeros(binNum));
        } else {
          stringBuilder.append(binNum);
        }
      }
    }
    return Integer.parseInt(stringBuilder.toString(), 2);
  }

  private int readFileLength(DataInputStream socketReader) throws IOException {
    byte[] resultArray = new byte[SIZE];
    int read = socketReader.read(resultArray, 0, SIZE);
    if (read <= -1) {
      System.err.println("Can't read data from socket");
    }
    return convertBytesToInt(resultArray);
  }

  private void saveFile(DataInputStream socketReader, String fileName, int fileLength)
      throws IOException {
    int size = Math.min(fileLength, 10000);
    FileOutputStream outputStream = new FileOutputStream(new File(fileName), true);
    while (fileLength != 0) {
      if (fileLength < size) {
        size = fileLength;
      }
      byte[] partOfFile = new byte[size];
      int status = socketReader.read(partOfFile, 0, size);
      if (status < 0) {
        System.err.println("Can't read data from socket");
      }
      outputStream.write(partOfFile);
      fileLength -= size;
    }
    outputStream.close();
  }

  public void runServer(int port) {
    try {
      server = new ServerSocket(port);
      System.out.println("Server started");
      System.out.println("Waiting for a client ...");

      socket = server.accept();
      System.out.println("Client accepted");

      DataInputStream socketRead = new DataInputStream(
          new BufferedInputStream(socket.getInputStream()));
      DataOutputStream socketWrite = new DataOutputStream(socket.getOutputStream());
      writeInSocket(socketWrite, "Connection successful");

      String request = "";
      String command = "";
      while (!command.equals("exit")) {
        request = readMessage(socketRead);
        System.out.println(request);
        String[] words = request.split("\\s");
        command = words[0].toLowerCase();
        if (!command.isEmpty()) {
          switch (command) {
            case "echo":
              writeInSocket(socketWrite, executeEcho(words));
              break;
            case "time":
              writeInSocket(socketWrite, executeTime());
              break;
            case "exit":
              writeInSocket(socketWrite, "Server start closing connection");
              break;
            case "upload":
//              Files.write(Paths.get(WORK_DIRECTORY_PATH + words[1]), readBytes(socketRead));
              int fileLength = readFileLength(socketRead);
              saveFile(socketRead, WORK_DIRECTORY_PATH + words[1], fileLength);
              writeInSocket(socketWrite, "File " + words[1] + " successfully received and saved");
              break;
            default:
              writeInSocket(socketWrite, "Command not found");
              Thread.sleep(100);
              break;
          }
        }
      }
      System.out.println("Closing connection");
      closeConnection(socket, socketRead, socketWrite);
    } catch (IOException | InterruptedException i) {
      System.err.println("Exception: " + i);
    }
  }

  public static void main(String args[]) {
    new Server().runServer(5003);
  }
}

