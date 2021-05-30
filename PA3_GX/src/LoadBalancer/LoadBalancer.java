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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class LoadBalancer{
            
    private Socket clientSocket;
    private DataOutputStream dout;
    private Map<Integer, Integer> serverMap = new HashMap<Integer, Integer>();
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
                String[] msg = message.split("|");
                
                
                if (msg[0].equals("client")){
                    
                    // Send message to server ...
                    // client message example: client | client id | request id | 00 | 01 | number of iterations | 0 |
                    String serverMessage = "request|" + msg[5];
                    int serverId = 0;
                    int port = serverMap.get(serverId);   // <- TODO change this
                    Socket server = new Socket("127.0.0.1",port);
                    DataOutputStream dout = new DataOutputStream(server.getOutputStream());
                    dout.writeUTF(serverMessage);
                    dout.flush();
                
                    // Server response message:  server|02|Constante ou server|03|0 (caso erro)
                    DataInputStream server_dis=new DataInputStream(server.getInputStream()); 
                    String serverResponse=dis.readUTF().strip();
                    System.out.print(serverResponse);
                    
                    
                    // Client response
                    String clientResponse = msg[1] + "|" + msg[2] + "|" + serverId + "|" + serverResponse.split("|")[0] + "|" + msg[5] + "|" + serverResponse.split("|")[1];
                    DataOutputStream clientDout = new DataOutputStream(clientSocket.getOutputStream());
                    clientDout.writeUTF(clientResponse);
                    clientDout.flush();
                    
                    // Closing connection
                    clientSocket.close();
                    System.out.print("connection with client terminated");
                    
                }
                else if(msg[0].equals("monitor")){
                    
                    updateMonitor(msg);
                    
                    while (true) {
                        message=dis.readUTF().strip();
                        System.out.print(message);
                        msg = message.split("|");
                        updateMonitor(msg);
                    }
                }
            } 
            catch(Exception e){
                System.out.println(e);
            }  
        }
        
        
        
        public void updateMonitor(String[] msg) throws IOException{
            
            // msg example: monitor|conection|server id|server port
            if (msg[1].equals("connect")){
                int server_id = Integer.parseInt(msg[2]);
                int port = Integer.parseInt(msg[3].strip());
                serverMap.put(server_id, port);
            }
            else if (msg[1].equals("disconnect")){
                int server_id = Integer.parseInt(msg[2].strip());
                int port = Integer.parseInt(msg[3]);
                serverMap.remove(server_id);
                // TODO resend to another server
            }            
        }
    }
    
}