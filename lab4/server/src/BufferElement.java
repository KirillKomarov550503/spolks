import java.net.SocketAddress;
import java.util.Arrays;

public class BufferElement {

  private int id;
  private byte[] message;
  private Thread thread;
  private volatile boolean isWaitAck;
  private SocketAddress socketAddress;

  public BufferElement() {
    id = 0;
  }

  public BufferElement(int id, byte[] message, SocketAddress socketAddress) {
    this.id = id;
    this.message = message;
    this.socketAddress = socketAddress;
    isWaitAck = false;

  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public void setSocketAddress(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public int getId() {
    return id;
  }


  public byte[] getMessage() {
    return message;
  }


  public Thread getThread() {
    return this.thread;
  }


  public void waitAck() {
    thread = new Thread(() -> {
      try {
        isWaitAck = true;
        Thread.sleep(150);
        isWaitAck = false;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    thread.start();
  }

  public boolean isWaitAck() {
    return isWaitAck;
  }

  public void setWaitAck(boolean waitAck) {
    isWaitAck = waitAck;
  }

  @Override
  public String toString() {
    return "BufferElement{" +
        "id=" + id +
        ", message=" + Arrays.toString(message) +
        ", thread=" + thread +
        ", isWaitAck=" + isWaitAck +
        ", socketAddress=" + socketAddress +
        '}';
  }
}
