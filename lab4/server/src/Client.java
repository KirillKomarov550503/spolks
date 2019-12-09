import java.io.FileOutputStream;
import java.net.SocketAddress;
import java.util.Arrays;

public class Client {

  private String clientId;
  private String command;
  private SocketAddress socketAddress;
  private byte[] message;
  private byte commandType;
  private boolean isStartExecute;

  public Client(String clientId, String command, SocketAddress socketAddress) {
    this.clientId = clientId;
    this.command = command;
    this.socketAddress = socketAddress;
    this.isStartExecute = false;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public boolean isStartExecute() {
    return isStartExecute;
  }

  public void setStartExecute(boolean startExecute) {
    isStartExecute = startExecute;
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public void setSocketAddress(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public byte[] getMessage() {
    return message;
  }

  public void setMessage(byte[] message) {
    this.message = message;
  }

  public byte getCommandType() {
    return commandType;
  }

  public void setCommandType(byte commandType) {
    this.commandType = commandType;
  }

  @Override
  public String toString() {
    return "Client{" +
        "clientId='" + clientId + '\'' +
        ", command='" + command + '\'' +
        ", socketAddress=" + socketAddress +
        ", message=" + Arrays.toString(message) +
        ", commandType=" + commandType +
        '}';
  }
}
