package data;


public class FCbuffer {

  private final long windowSize;
  
  private long sendBase;
  
  private FCpacket[] innerBuffer;
  
  private int next = 0;
  
  private int first = 0;
  
  public FCbuffer(long windowSize, Long sendBase) {
    this.windowSize = windowSize;
    
    this.innerBuffer = new FCpacket[(int) windowSize];
    this.sendBase = sendBase;
  }
  
  public long getWindowSize() {
    return this.windowSize;
  }
  
  /**
   * 
   * @return true when buffer is full, so there are no free slots.
   */
  public boolean isFull() {
    return this.innerBuffer.length == this.windowSize
        && this.first == this.next;
  }
  
  public boolean isEmpty() {
    return this.innerBuffer.length == 0;
  }
  
  public boolean add(FCpacket packet) {
    if (this.isFull()) {
      return false;
    }
    this.innerBuffer[(int) this.next] = packet;
    this.incrementNext();
    return true;
  }
  
  public void markAsACK(FCpacket packet) {
    this.markAsACK(packet.getSeqNum());
  }
  
  public void markAsACK(Long seqNum) {
    for (int i = 0; i < this.innerBuffer.length; i++) {
      FCpacket packet = this.innerBuffer[i];
      if (packet.getSeqNum() == seqNum) {
        packet.setValidACK(true);
        break;
      }
    }
    this.removeAckedPackages(seqNum);
  }
  
  public void removeAckedPackages(long seqNum) {
    if (seqNum == this.sendBase) {
      for (int i = 0; i < this.innerBuffer.length; i++) {
        FCpacket packet = this.innerBuffer[i];
        if (packet.isValidACK()) {
          this.incrementFirst();
          this.sendBase = packet.getSeqNum();
        }
      }
    }
  }
  
  /**
   * @return null when packet not found and 
   *         when found, packet with seqNum
   */
  public FCpacket getBySeqNum(Long seqNum) {
    if (this.isEmpty()) {
      throw new IllegalArgumentException("It's empty!");
    }
    for (int i = 0; i < this.innerBuffer.length; i++) {
      FCpacket packet = this.innerBuffer[i];
      if (packet != null && packet.getSeqNum() == seqNum) {
        return packet;
      }      
    }
    throw new IllegalArgumentException("Not found");
  }
  
  private void incrementNext() {
    int intWindowSize = (int) this.windowSize;
    this.next = (((this.next+1) % intWindowSize) + intWindowSize) % intWindowSize;
  }
  
  private void incrementFirst() {
    int intWindowSize = (int) this.windowSize;
    this.first = (((this.first+1) % intWindowSize) + intWindowSize) % intWindowSize;
  }
}
