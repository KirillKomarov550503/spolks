package com.kamarou.spolks.socket;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {

  private Scanner scanner = new Scanner(System.in);
  private static final String WORK_DIRECTORY_PATH = "C:\\Users\\kirya\\Desktop\\7 сем\\spolks\\lab1\\work_directory_for_client\\";

  private byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }

  private String readMessage(DataInputStream socketReader) throws IOException {
    StringBuilder message = new StringBuilder();
    byte symbol;
    while ((symbol = socketReader.readByte()) != ((byte) '\n')) {
      if (symbol > 31) {
        message.append((char) symbol);
      }
    }
    return message.toString();
  }

  private void writeInSocket(DataOutputStream socketWrite, String message) throws IOException {
    socketWrite.writeUTF(message + "\n");
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

  private byte[] convertBinaryStringToByteArray(String binaryArray) {
    List<Byte> bytes = new ArrayList<>();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < binaryArray.length(); i++) {
      builder.append(binaryArray.charAt(i));
      if ((i + 1) % 7 == 0 || (i + 1) == binaryArray.length()) {
        System.out.print(builder.toString() + " ");
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

  public void runClient(String address, int port) {
    try {
      Socket socket = new Socket(address, port);
      System.out.println("Connected");

      DataOutputStream socketWriter = new DataOutputStream(socket.getOutputStream());
      socketWriter.flush();
      DataInputStream socketReader = new DataInputStream(
          new BufferedInputStream(socket.getInputStream()));
      System.out.println("Response from server: " + readMessage(socketReader));
      String line = "";
      while (!line.equals("exit")) {
        System.out.print("Enter command: ");
        line = scanner.nextLine();
        char delimeter = '\n';
        line += new String(new byte[]{(byte) delimeter});
        writeInSocket(socketWriter, line);
        String[] words = line.split("\\s");
        if (words[0].toLowerCase().equals("upload")) {
          socketWriter.write(concatArrays(readFile(WORK_DIRECTORY_PATH + words[1]), new byte[]{(byte)'\n'}));
          //socketWriter.write(concatArrays(readFile("C:\\work_directory\\client\\azaza.txt"), new byte[]{(byte)'\n'}));
          socketWriter.flush();
//          byte[] bytes = readFile(WORK_DIRECTORY_PATH + "Voprosy_ekz_Sist_Analiz_dnev_18-19 (1).doc");

        }
        System.out.println("Response from server: " + readMessage(socketReader));
      }
      closeConnection(socket, socketReader, socketWriter);
    } catch (IOException u) {
      System.err.println("IOException: " + u);
    }
  }

  public static void main(String args[]) {
    new Client().runClient("127.0.0.1", 5002);
  }
}
