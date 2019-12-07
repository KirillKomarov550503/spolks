import java.io.FileOutputStream;
import java.net.SocketAddress;
import java.util.Arrays;

public class Client {

  private String clientId;
  private String command;
  private CommandStatus status;
  private boolean isReceiveClientDuplicate;
  private SocketAddress socketAddress;
  private FileOutputStream outputStream;
  private int messageLength;
  private int leftFileLength;
  private int index;
  private int from;
  private int to;
  private int size;
  private boolean isPresentFileLength;
  private int fileLength;
  private boolean isInitialComplete;
  private byte[] message;
  private byte commandType;
  private boolean isPresentData;

  public Client(String clientId) {
    this.clientId = clientId;
    this.status = CommandStatus.NOT_STARTED;
    this.isReceiveClientDuplicate = false;

  }

  public Client(String clientId, String command, SocketAddress socketAddress) {
    this.clientId = clientId;
    this.command = command;
    this.socketAddress = socketAddress;
    this.isInitialComplete = false;
    this.isPresentFileLength = false;
  }

  public Client(String clientId, String command, CommandStatus status,
      boolean isReceiveClientDuplicate, SocketAddress socketAddress) {
    this.clientId = clientId;
    this.command = command;
    this.status = status;
    this.isReceiveClientDuplicate = isReceiveClientDuplicate;
    this.socketAddress = socketAddress;
  }

  public byte[] getMessage() {
    return message;
  }

  public void setMessage(byte[] message) {
    this.message = message;
  }

  public FileOutputStream getOutputStream() {
    return outputStream;
  }

  public boolean isPresentFileLength() {
    return isPresentFileLength;
  }

  public void setPresentFileLength(boolean presentFileLength) {
    isPresentFileLength = presentFileLength;
  }

  public int getFileLength() {
    return fileLength;
  }

  public void setFileLength(int fileLength) {
    this.fileLength = fileLength;
  }

  public boolean isInitialComplete() {
    return isInitialComplete;
  }

  public void setInitialComplete(boolean initialComplete) {
    isInitialComplete = initialComplete;
  }

  public int getFrom() {
    return from;
  }

  public int getMessageLength() {
    return messageLength;
  }

  public void setMessageLength(int messageLength) {
    this.messageLength = messageLength;
  }

  public void setFrom(int from) {
    this.from = from;
  }

  public int getTo() {
    return to;
  }

  public void setTo(int to) {
    this.to = to;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public void setOutputStream(FileOutputStream outputStream) {
    this.outputStream = outputStream;
  }

  public int getLeftFileLength() {
    return leftFileLength;
  }

  public void setLeftFileLength(int leftFileLength) {
    this.leftFileLength = leftFileLength;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public void setSocketAddress(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public boolean isReceiveClientDuplicate() {
    return isReceiveClientDuplicate;
  }

  public void setReceiveClientDuplicate(boolean receiveClientDuplicate) {
    isReceiveClientDuplicate = receiveClientDuplicate;
  }

  public String getClientId() {
    return clientId;
  }

  public CommandStatus getStatus() {
    return status;
  }

  public void setStatus(CommandStatus status) {
    this.status = status;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public byte getCommandType() {
    return commandType;
  }

  public void setCommandType(byte commandType) {
    this.commandType = commandType;
  }

  public boolean isPresentData() {
    return isPresentData;
  }

  public void setPresentData(boolean presentData) {
    isPresentData = presentData;
  }

  @Override
  public String toString() {
    return "Client{" +
        "clientId='" + clientId + '\'' +
        ", command='" + command + '\'' +
        ", status=" + status +
        ", isReceiveClientDuplicate=" + isReceiveClientDuplicate +
        ", socketAddress=" + socketAddress +
        ", outputStream=" + outputStream +
        ", messageLength=" + messageLength +
        ", leftFileLength=" + leftFileLength +
        ", index=" + index +
        ", from=" + from +
        ", to=" + to +
        ", size=" + size +
        ", isPresentFileLength=" + isPresentFileLength +
        ", fileLength=" + fileLength +
        ", isInitialComplete=" + isInitialComplete +
        ", message=" + Arrays.toString(message) +
        ", commandType=" + commandType +
        ", isPresentData=" + isPresentData +
        '}';
  }
}
