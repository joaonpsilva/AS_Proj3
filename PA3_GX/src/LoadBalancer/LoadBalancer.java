/**
* This package contains the classes and methods to play the Load Balancer role. 
* Please read the requirements carefully.
* 
**/

package LoadBalancer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class LoadBalancer{
            
    private Socket clientSocket;
    private DataOutputStream dout;
    final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(100);

    public LoadBalancer(){
        
    }
    
    public void startServer(int port) {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Waiting for clients to connect...");
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("OIS server connected to: " + client);
                    clientProcessingPool.submit(new ClientTask(client));
                }
            } 
            catch(IOException e){

            }
            
            }    
        };  
        
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }
    
    
    
    private class ClientTask implements Runnable {
    private final Socket clientSocket;

        private ClientTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            System.out.println("Connected to client");
            
            // Rec
            try{
                DataInputStream dis=new DataInputStream(clientSocket.getInputStream()); 
                String  message=dis.readUTF().strip();
                System.out.print(message);
                
                
                DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
                String message2 = "LB message";
                dout.writeUTF(message2);
                dout.flush();
                
                clientSocket.close();
                System.out.print("connection with client terminated");
            } 
            catch(Exception e){
                System.out.println(e);
            }  
        } 
    }
    
}