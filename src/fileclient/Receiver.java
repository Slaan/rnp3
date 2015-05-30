package fileclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import data.FCbuffer;
import data.FCpacket;

public class Receiver extends Thread {

  private FCbuffer buffer;
  
  private DatagramSocket socket;
  
  public Receiver(FCbuffer buffer, DatagramSocket socket) {
    super("Receiver");
    this.buffer = buffer;
    this.socket = socket;
  }
  
  @Override
  public void run() {
    boolean done = false;
    DatagramPacket packet = null;
    while (!done) {
      try {
        socket.receive(packet);
        FCpacket fcPacket = new FCpacket(packet.getData(), packet.getLength());
        cancelTimer(fcPacket);
        buffer.markAsACK(fcPacket);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  public void cancelTimer(FCpacket packet) {
    /* Cancel timer for the given FCpacket */
    testOut("Cancel Timer for packet" + packet.getSeqNum());

    if (packet.getTimer() != null) {
      packet.getTimer().interrupt();
    }
  }
  
  public void testOut(String out) {
    if (FileCopyClient.TEST_OUTPUT_MODE) {
      System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread
          .currentThread().getName(), out);
    }
  }
  
}
