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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


// Client para monitor

// Server para loadbalancer e monitor

class Server{
            
    private Socket monitorSocket;
    private Socket serverSocket;
    final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(3);
    private static int serverId;
    private static int serverport;
 
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
            String  receivedMessage = dis.readUTF().strip();
            String[] message = receivedMessage.split("|");
            System.out.println("Server id: " + message[1]);
            this.serverId = Integer.parseInt(message[1]);
            this.serverport = Integer.parseInt(message[2]);
            
            while (true) {
                //Esperar mensagem
                
            }
            
        }catch(IOException e){
                System.err.println("");
            }
        }
    
    public void listenLB(){
        try {
            ServerSocket serverSocket = new ServerSocket(serverport);
            System.out.println("Waiting for clients to connect...");
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("OIS server connected to: " + client);
                
            }
            } 
            catch(IOException e){

            }
        
    }
    
    
}