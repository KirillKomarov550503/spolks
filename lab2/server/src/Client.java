public class Client {

  private String clientId;
  private String lastCommand;
  private CommandStatus status;
  private boolean isReceiveClientDuplicate;

  public Client(String clientId) {
    this.clientId = clientId;
    this.status = CommandStatus.NOT_STARTED;
    this.isReceiveClientDuplicate = false;
  }

  public Client(String clientId, String lastCommand) {
    this.clientId = clientId;
    this.lastCommand = lastCommand;
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

  public String getLastCommand() {
    return lastCommand;
  }

  public void setLastCommand(String lastCommand) {
    this.lastCommand = lastCommand;
  }
}
