import java.net.SocketAddress;

public class ReadSegment {
  private int messageId;
  private byte type;
  private String clientId;
  private SocketAddress socketAddress;

  public ReadSegment() {
  }

  public ReadSegment(int messageId, byte type, String clientId, SocketAddress socketAddress) {
    this.messageId = messageId;
    this.type = type;
    this.clientId = clientId;
    this.socketAddress = socketAddress;
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public void setSocketAddress(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public int getMessageId() {
    return messageId;
  }

  public byte getType() {
    return type;
  }

  public void setMessageId(int messageId) {
    this.messageId = messageId;
  }

  public void setType(byte type) {
    this.type = type;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }
}
