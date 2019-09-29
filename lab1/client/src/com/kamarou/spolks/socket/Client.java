package com.kamarou.spolks.socket;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Client {

  private Scanner scanner = new Scanner(System.in);
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7 сем\\spolks\\lab1\\work_directory_for_client\\";
  private static final int SIZE = 5;

  private byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }

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

  private void writeFile(DataOutputStream socketWriter, byte[] file) throws IOException {
    int fileLength = file.length;
    int size = Math.min(fileLength, 10000);
    int from = 0;
    int to = from + size;
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
    }
  }

  private void writeInSocket(DataOutputStream socketWriter, String message) throws IOException {
    byte[] resBytes = concatArrays(new byte[]{(byte) -1}, message.getBytes());
    resBytes = concatArrays(resBytes, new byte[]{(byte) -1});
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < resBytes.length; i++) {
      builder.append((char) resBytes[i]);
    }
    socketWriter.writeUTF(builder.toString());
    socketWriter.flush();
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

  private String insertZeros(String binNum) {
    StringBuilder builder = new StringBuilder(binNum);
    int diff = 8 - binNum.length() - 1;
    for (int i = 0; i < diff; i++) {
      builder.insert(0, '0');
    }
    return builder.toString();
  }

  private byte[] convertBinaryStringToByteArray(String binaryArray) {
    List<Byte> bytes = new ArrayList<>();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < binaryArray.length(); i++) {
      builder.append(binaryArray.charAt(i));
      if ((i + 1) % 7 == 0 || (i + 1) == binaryArray.length()) {
        bytes.add(Byte.parseByte(builder.toString(), 2));
        builder.delete(0, builder.length());
      }
    }
    byte[] initArray = new byte[0];
    for (Byte bt : bytes) {
      initArray = concatArrays(initArray, new byte[]{bt});
    }
    return initArray;
  }

  private void writeFileLength(DataOutputStream socketWriter, byte[] file) throws IOException {
    int fileLength = file.length;
    byte[] bytes = convertBinaryStringToByteArray(Integer.toBinaryString(fileLength));
    if (bytes.length < SIZE) {
      byte[] temp = new byte[SIZE - bytes.length];
      Arrays.fill(temp, (byte) -1);
      socketWriter.write(concatArrays(bytes, temp));
      socketWriter.flush();
      return;
    }
    socketWriter.write(bytes);
    socketWriter.flush();
  }

  private void executeDownloadCommand(DataOutputStream socketWriter, String fileName)
      throws IOException {
    if (!new File(WORK_DIRECTORY_PATH + fileName).exists()) {
      String error = String.format("File with name %s not found", fileName);
      System.err.printf(error);
      writeFileLength(socketWriter, new byte[]{});
      writeInSocket(socketWriter, error);
    } else {
      byte[] file = readFile(WORK_DIRECTORY_PATH + fileName);
      writeFileLength(socketWriter, file);
      writeFile(socketWriter, file);
      writeInSocket(socketWriter,
          "File " + fileName + " was sucessfully uploaded for client");
    }
  }

  public void runClient(String address, int port) {
    try {
      Socket socket = new Socket(address, port);
      System.out.println("Connected");

      DataOutputStream socketWriter = new DataOutputStream(socket.getOutputStream());
      DataInputStream socketReader = new DataInputStream(
          new BufferedInputStream(socket.getInputStream()));
      System.out.println("Response from server: " + readMessage(socketReader));
      String line = "";
      while (!line.equals("exit\n")) {
        System.out.print("Enter command: ");
        line = scanner.nextLine();
        char delimeter = '\n';
        line += new String(new byte[]{(byte) delimeter});
        writeInSocket(socketWriter, line);
        String[] words = line.split("\\s");
        switch (words[0].toLowerCase()) {
          case "upload":

            if (new File(WORK_DIRECTORY_PATH + words[1]).exists()) {
              byte[] file = readFile(WORK_DIRECTORY_PATH + words[1]);
              writeFileLength(socketWriter, file);
              writeFile(socketWriter, file);
            } else {
              System.out.println("File not found");
              writeFileLength(socketWriter, new byte[]{});
              continue;
            }
            break;
          case "download":
            int fileLength = readFileLength(socketReader);
            if (fileLength != 0) {
              saveFile(socketReader, WORK_DIRECTORY_PATH + words[1], fileLength);
            }
            break;
        }
        System.out.println("Response from server: " + readMessage(socketReader));
      }
      closeConnection(socket, socketReader, socketWriter);
    } catch (IOException u) {
      System.err.println("IOException: " + u);
    }
  }

  public static void main(String args[]) {
    new Client().runClient("127.0.0.1", 5003);
  }
}
