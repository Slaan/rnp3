package fileclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class Receiver extends Thread {

  public final static boolean TEST_OUTPUT_MODE = true;
  private LinkedList<FCpacket> buffer;
  private DatagramSocket socket;
  private Semaphore lock;
  private FileCopyClient client;

  public Receiver(LinkedList<FCpacket> buffer2, DatagramSocket socket, 
      Semaphore bufferlock, FileCopyClient fcc) {
    super("Receiver");
    this.buffer = buffer2;
    this.socket = socket;
    this.lock = bufferlock;
    this.client = fcc;
  }

  @Override
  public void run() {
    boolean done = false;
    byte[] data = new byte[FileCopyClient.UDP_PACKET_SIZE];
    while (!done) {
      DatagramPacket packet = new DatagramPacket(data, data.length);
      try {
        socket.receive(packet);
      } catch (SocketTimeoutException e1) {
        done=true;
        System.out.println("Catchblock");
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } 
      FCpacket ackpack = new FCpacket(packet.getData(), packet.getLength());
      try {
        lock.acquire();
        for (FCpacket part : buffer) {
          if (part.equals(ackpack)) {
            part.setValidACK(true);
            long rtt = System.nanoTime()-part.getTimestamp();
            client.computeTimeoutValue(rtt);
            client.add_total_rtt(rtt);
            part.getTimer().interrupt();
            break;
          }
        }
        client.increment_received_acks();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } finally {
        lock.release();
      }
      if (!buffer.isEmpty()) {
        if (0 == buffer.get(0).compareTo(ackpack)) {
          updateBuffer();
        }
      }
    }
    System.out.println("end of receiver");
  }
  

  public void updateBuffer() {
    try {
      lock.acquire();
      boolean done = false;
      while (!done) {
        if (buffer.isEmpty()) {
          done = true;
          break;
        } else {
          if (buffer.get(0).isValidACK()) {
            buffer.removeFirst();
          } else {
            done = true;
            System.out.println("Buffersize after udpate: "+buffer.size());
            break;
          }
        }
      }
      testOut("Buffer Updated!");
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      lock.release();      
    }
  }

  public void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread.currentThread().getName(), out);
    }
  }
}
