/**
* This package contains the classes and methods to play the Server role.
* Please read the requirements carefully.
* 
**/

package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


// Client para monitor

// Server para loadbalancer e monitor

class Server{
            
    private Socket monitorSocket;
    private Socket serverSocket;
    private static int serverId;
    private static int serverport;
    private static String avogrado = "602214076";
    private BlockingQueue<Socket> queue = new LinkedBlockingDeque<>(2);
 
    public Server(){
        for (int i = 0; i < 3; i++)
            new ServerThread().start();
    }
    
    
    // Connect to monitor and LB
    public void connect(int port){
        
        int sleepTimer = 1000;
        boolean connected = false;
        try{
            System.out.println("Connecting to monitor");
            while (!connected){
                try {
                    monitorSocket = new Socket("127.0.0.1",port);        // monitor port
                    connected = true;
                } catch (IOException e) {
                    System.err.println("Error connecting to Monitor, trying again in " + sleepTimer/1000 + "  seconds");
                    Thread.sleep(sleepTimer);
                }
            }
            System.out.println("Connection with monitor estabelished");

            
            // Send ID request
            DataOutputStream dout = new DataOutputStream(monitorSocket.getOutputStream());
            String msg = "Server|id_request";
            dout.writeUTF(msg);  
            dout.flush();  
            
            // Receive ID 
            DataInputStream dis=new DataInputStream(monitorSocket.getInputStream());  
            String receivedMessage = dis.readUTF().strip();
            String[] message = receivedMessage.split("\\|");
            System.out.println("Server id: " + message[1]);
            this.serverId = Integer.parseInt(message[1]);
            this.serverport = Integer.parseInt(message[2]);
            
            // start thread to connect to LB
            ListenLB(this.serverport);
            
            //Receive HeartBeats
            while (true) {
                receivedMessage = dis.readUTF().strip();
                message = receivedMessage.split("\\|");
                assert(message[1].equals("HeartBeat"));
                
                String responseMsg = "Server|" + serverId + "|HeartBeat";
                dout.writeUTF(msg);  
                dout.flush();  
            }
            
        }catch(Exception e){
            System.err.println("Error");
            System.err.println(e);
        }
    }
    
    
    
    // Listen to LB requests
    public void ListenLB(int port) {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(port);
                    System.out.println("Waiting for clients requests");
                    while (true) {
                        Socket client = serverSocket.accept();
                        System.out.println("New request");
                        boolean availableSlot = queue.offer(client); // check available slot to handle request

                        if (availableSlot==false){
                            System.out.println("Request Denied - to many requests to handle");
                            DataOutputStream dout = new DataOutputStream(client.getOutputStream());
                            dout.writeUTF("03|0");
                            dout.flush();
                        }
                    }
                } 
                catch(IOException e){

                }
            
            }    
        };  
        
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }
        
    
    // Thread that handles client
    private class ServerThread extends Thread {
        private ServerThread() {
            System.out.println("Working Thread Initiated");
        }
        @Override
        public void run() {
            
            while(true){
                try{
                    Socket clientSocket = queue.take();
                    DataInputStream dis=new DataInputStream(clientSocket.getInputStream()); 
                    String  message=dis.readUTF().strip().split("\\|")[1];
                    int iterations = Integer.parseInt(message);
                    System.out.println("Viewing request. Calculating with iterations: " + iterations);

                    String avogradoIteration = avogrado.substring(0, iterations);
                    Thread.sleep(5000 * iterations);
                    
                    System.out.println("Responding: 02|" + avogradoIteration);
                    DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
                    dout.writeUTF("02|" + avogradoIteration );
                    dout.flush();
                    clientSocket.close();

                } 
                catch(Exception e){
                    System.out.println(e);
                }  
            }
        }    
    }
}