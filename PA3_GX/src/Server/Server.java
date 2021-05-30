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
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;


// Client para monitor

// Server para loadbalancer e monitor

class Server{
            
    private Socket monitorSocket;
    private Socket serverSocket;
    private static int serverId;
    private static int serverport;
    private static String avogrado = "602214076";
    private BlockingQueue<Socket> queue = new LinkedBlockingDeque<>(5);
 
    public Server(){
        
    }
    
    public void connect(int port){
        
        boolean connected = false;
        try{
            System.out.println("Trying to connect to server");
            monitorSocket = new Socket("127.0.0.1",port);        // monitor port
            DataOutputStream dout = new DataOutputStream(monitorSocket.getOutputStream());
            System.out.println("Connection initiated");

            // Send id request
            String msg = "Server|id_request";
            dout.writeUTF(msg);  
            dout.flush();  
            
            DataInputStream dis=new DataInputStream(monitorSocket.getInputStream());  
            String receivedMessage = dis.readUTF().strip();
            String[] message = receivedMessage.split("|");
            System.out.println("Server id: " + message[1]);
            this.serverId = Integer.parseInt(message[1]);
            this.serverport = Integer.parseInt(message[2]);
            
            ListenLB(this.serverport);
            
            //Receive HeartBeats
            while (true) {
                receivedMessage = dis.readUTF().strip();
                message = receivedMessage.split("|");
                
                assert(message[1].equals("HeartBeat"));
                
                String responseMsg = "Server|" + serverId + "|HeartBeat";
                dout.writeUTF(msg);  
                dout.flush();  
            }
            
        }catch(IOException e){
                System.err.println("ERROR");
        }
    }
    
    
    
    
    public void ListenLB(int port) {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Waiting for clients to connect...");
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("Server connected to: " + client);
                    //new ClientTask(client).start();
                    boolean a = queue.offer(client);
                    
                    if (a==false){
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
        
    
    
    private class ServerTask extends Thread {
        private ServerTask() {
            
        }
        @Override
        public void run() {

            try{
                Socket clientSocket = queue.take();
                DataInputStream dis=new DataInputStream(clientSocket.getInputStream()); 
                String  message=dis.readUTF().strip();
                int iterations = Integer.parseInt(message);
                System.out.print(message);
                
                String avogradoIteration = avogrado.substring(0, iterations);
                Thread.sleep(5000 * iterations);
                
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