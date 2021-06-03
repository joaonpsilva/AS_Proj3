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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class LoadBalancer{
            
    private Socket clientSocket;
    private DataOutputStream dout;
    private Map<Integer, ServerInfo> serverMap = new HashMap<Integer, ServerInfo>();
    final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(100);
    private final int monitorPort = 3001;
    
    public LoadBalancer(){
        
    }
    
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
                
                
            if (msg[0].equals("client")){

                // Send message to server ...
                // client message example: client | client id | request id | 00 | 01 | number of iterations | 0 |
                System.out.println("new client request");
                
                //MAKE CONNECTION WITH MONITOR
                Socket monitor;
                DataOutputStream monitor_dout;
                DataInputStream monitor_dis;
                boolean serverNotCrashed = false;
                
                while (!serverNotCrashed){
                    
                    int serverId = -1;
                    int serverport = -1;
                    
                    try{
                    
                        //ask monitor for servers status
                        monitor = new Socket("127.0.0.1", monitorPort);
                        monitor_dout = new DataOutputStream(monitor.getOutputStream());
                        monitor_dis=new DataInputStream(monitor.getInputStream());
                        
                        System.out.println("Asking server status");
                        //send message to monitor
                        String monitorMessage = "LoadBalancer|serverInfo";
                        monitor_dout.writeUTF(monitorMessage);
                        monitor_dout.flush();

                        //receive message from monitor
                        //monitor|serverID, serverPort, ActiveReqs|....
                        String monitorResponse[] = monitor_dis.readUTF().strip().split("\\|");
                        
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
                        monitor_dout.writeUTF(monitorMessage);
                        monitor_dout.flush();
                        
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
                        
                    }catch(Exception e){
                        System.out.println("Server crashed. Choosing another server");

                        continue;
                    } 
                    
                    String clientResponse = msg[1] + "|" + msg[2] + "|" + serverId + "|" + serverResponse.split("\\|")[0] + "|" + msg[5] + "|" + serverResponse.split("\\|")[1];
                    
                    try{
                        //INFORM MONITOR ABOUT CHOSEN SERVER
                        String monitorMessage = "LoadBalancer|ReceivedMessage|" + clientResponse;
                        monitor_dout.writeUTF(monitorMessage);
                        monitor_dout.flush();
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
                /*else if(msg[0].equals("monitor")){
                    
                    updateMonitor(msg);
                    
                    while (true) {
                        message=dis.readUTF().strip();
                        System.out.println(message);
                        msg = message.split("\\|");
                        updateMonitor(msg);
                    }
                }*/
            
        }
        
        
        
        /*public void updateMonitor(String[] msg) throws IOException{
            
            // msg example: monitor|conection|server id|server port
            if (msg[1].equals("connect")){
                int server_id = Integer.parseInt(msg[2]);
                int port = Integer.parseInt(msg[3].strip());
                serverMap.put(server_id, new ServerInfo(server_id, port));
            }
            else if (msg[1].equals("disconnect")){
                int server_id = Integer.parseInt(msg[2].strip());
                int port = Integer.parseInt(msg[3]);
                serverMap.remove(server_id);
                // TODO resend to another server
            }            
        }*/
        
    }
    
}