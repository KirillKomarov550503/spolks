public class ReadSegment {
  private int messageId;
  private byte type;

  public ReadSegment() {
  }

  public ReadSegment(int messageId, byte type) {
    this.messageId = messageId;
    this.type = type;
  }

  public int getMessageId() {
    return messageId;
  }

  public byte getType() {
    return type;
  }
}
