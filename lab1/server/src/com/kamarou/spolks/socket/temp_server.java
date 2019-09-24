import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

public class Server {


  private Socket socket;
  private ServerSocket server;
  private static final String WORK_DIRECTORY_PATH = "C:\\work_directory\\server\\";
  
  private byte[] readBytes(DataInputStream socketReader) throws IOException {
	  byte[] array1 = new byte[0];
	  byte symbol;
	  byte[] resultArray = new byte[0];
	  while ((symbol = socketReader.readByte()) != ((byte) '\n')) {
	      if (symbol > 31) {
	    	  array1 = resultArray;
	    	  byte[] array2 = new byte[]{symbol};
	    	  resultArray = new byte[array1.length + array2.length];
	    	  System.arraycopy(array1, 0, resultArray, 0, array1.length);
	    	  System.arraycopy(array2, 0, resultArray, 0, array2.length);
	      }
	    }
	  return resultArray;
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


  private String executeEcho(String[] request) {
    return Arrays.stream(request).skip(1).collect(Collectors.joining(" "));
  }

  private String executeTime() {
    Date date = new Date();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
    return dateFormat.format(date);
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
            case "upload": Files.write(Paths.get(WORK_DIRECTORY_PATH + words[1]), readBytes(socketRead));
            	break;
            default:
              writeInSocket(socketWrite, "Command not found");
              break;
          }
        }
      }
      System.out.println("Closing connection");
      closeConnection(socket, socketRead, socketWrite);
    } catch (IOException i) {
      System.err.println("Exception: " + i);
    }
  }

  public static void main(String args[]) {
    new Server().runServer(5002);
  }
}
