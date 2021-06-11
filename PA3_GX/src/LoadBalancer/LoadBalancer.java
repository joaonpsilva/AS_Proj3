/**
* This package contains the classes and methods to play the Load Balancer role. 
* Please read the requirements carefully.
* 
**/

package LoadBalancer;

import Monitor.ServerInfo;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;


class LoadBalancer{
            
    final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(100);
    private LoadBalancer_GUI ui;
    
    private CountDownLatch ServerCountDownLatch = new CountDownLatch(1);   // To wait until a server is connected before trying to send to servers
    private final int monitorPort = 3001;
    private DataOutputStream monitorout;
    private DataInputStream monitorin;
    private final ReentrantLock rl = new ReentrantLock( true );

    
    public LoadBalancer(LoadBalancer_GUI ui){
        this.ui = ui;
    }

    public LoadBalancer(){}
    
    public void startServer(int port) {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(port);
                    System.out.println("Waiting for connections");
                    while (true) {
                        Socket client = serverSocket.accept();
                        clientProcessingPool.submit(new ClientTask(client));
                    }
                } 
                catch(IOException e){}
            
            }    
        };  
        
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
        
        
        try {
            ServerSocket serverSocket = new ServerSocket(2999);
            System.out.println("Waiting for Monitor");
            Socket monitorSocket = serverSocket.accept();
            monitorout = new DataOutputStream(monitorSocket.getOutputStream());
            monitorin = new DataInputStream(monitorSocket.getInputStream()); 
        }
        catch(IOException e){

        }
        
    }
    
    
    private class ClientTask implements Runnable {
        private final Socket clientSocket;

        private ClientTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            
            // Rec
            String message;
            try{
                DataInputStream dis=new DataInputStream(clientSocket.getInputStream()); 
                message=dis.readUTF().strip();
                System.out.println(message);

            }catch(Exception e){
                System.out.println("Error with client");
                return;
            }
            
            String[] msg = message.split("\\|");
            ui.addMessage(message);    
                
            if (msg[0].equals("client")){

                // Send message to server ...
                // client message example: client | client id | request id | 00 | 01 | number of iterations | 0 |
                System.out.println("new client request");
                
                //MAKE CONNECTION WITH MONITOR
                boolean serverNotCrashed = false;
                while (!serverNotCrashed){
                    
                    int serverId = -1;
                    int serverport = -1;
                    
                    try{
                    
                        //ask monitor for servers status
                        System.out.println("Asking server status");
                        
                        String monitorMessage = "LoadBalancer|serverInfo";
                        
                        rl.lock();
                        monitorout.writeUTF(monitorMessage);
                        monitorout.flush();
                        //receive message from monitor
                        //monitor|serverID, serverPort, ActiveReqs|....
                        String monitorMsg = monitorin.readUTF().strip();
                        String monitorResponse[] = monitorMsg.split("\\|");
                        rl.unlock();
                        
                        ui.addMessage(monitorMsg); 
                        
                        System.out.println("There are " + (monitorResponse.length -1) + " online servers");
                        //Get the server with less active Requests
                        serverId = -1;
                        serverport = -1;
                        int leastActReqs = Integer.MAX_VALUE;
                        for (int i = 1; i < monitorResponse.length; i++){
                            String serverInfo[] = monitorResponse[i].split(",");
                            int instanceReqs = Integer.valueOf(serverInfo[2]);

                            if (instanceReqs < leastActReqs){
                                leastActReqs = instanceReqs;
                                serverId= Integer.valueOf(serverInfo[0]);
                                serverport=Integer.valueOf(serverInfo[1]);
                            }
                        }

                        if (serverId==-1){
                            System.out.println("Zero servers connected");
                            this.clientSocket.close();
                            return;
                        }
                        
                        //INFORM MONITOR ABOUT CHOSEN SERVER
                        monitorMessage = "LoadBalancer|sentMessage|" + message;
                        rl.lock();
                        monitorout.writeUTF(monitorMessage);
                        monitorout.flush();
                        rl.unlock();
                        
                    }catch(Exception e){
                        System.out.println("Error connecting with monitor");
                        return;
                    }


                    String serverResponse = "";
                    try{

                        //SEND REQUEST TO SERVER                    
                        System.out.println("Sending request to server " + serverId);

                        Socket server = new Socket("127.0.0.1",serverport);
                        DataOutputStream dout = new DataOutputStream(server.getOutputStream());
                        String serverMessage = "request|" + msg[5];
                        dout.writeUTF(serverMessage);
                        dout.flush();

                        // Server response message:  server|02|Constante ou server|03|0 (caso erro
                        DataInputStream server_dis=new DataInputStream(server.getInputStream()); 
                        serverResponse=server_dis.readUTF().strip();
                        System.out.println("Received: " + serverResponse + " from server " + serverId);
                        serverNotCrashed = true;
                        server.close();
                        
                    }catch(Exception e){
                        System.out.println("Server crashed. Choosing another server");
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                        }
                        continue;
                    } 
                    
                    String clientResponse = msg[1] + "|" + msg[2] + "|" + serverId + "|" + serverResponse.split("\\|")[0] + "|" + msg[5] + "|" + serverResponse.split("\\|")[1];
                    
                    try{
                        //INFORM MONITOR ABOUT CHOSEN SERVER
                        rl.lock();
                        String monitorMessage = "LoadBalancer|ReceivedMessage|" + clientResponse;
                        monitorout.writeUTF(monitorMessage);
                        monitorout.flush();
                        rl.unlock();
                    }catch(Exception e){
                        System.out.println("Error connecting with monitor");
                        return;
                    }
                    
                    
                    try{
                        // Client response
                        
                        DataOutputStream clientDout = new DataOutputStream(clientSocket.getOutputStream());
                        clientDout.writeUTF(clientResponse);
                        clientDout.flush();

                        // Closing connection
                        clientSocket.close();
                        System.out.println("Connection with client terminated");
                    }
                    catch(Exception e){
                        System.out.println("Error with client");
                        return;
                    } 
 
                }
            }
        }

    }
    
}