public class ReadSegment {
  private int messageId;
  private byte type;
  private String clientId;

  public ReadSegment() {
  }

  public ReadSegment(int messageId, byte type, String clientId) {
    this.messageId = messageId;
    this.type = type;
    this.clientId = clientId;
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
