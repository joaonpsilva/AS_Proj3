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
            DataInputStream dis;
            DataOutputStream dout;
            try{
                dis = new DataInputStream(clientSocket.getInputStream()); 
                dout= new DataOutputStream(clientSocket.getOutputStream());
                
                message=dis.readUTF().strip();
                System.out.println(message);

            }catch(Exception e){
                System.out.println("Error with client");
                return;
            }
            
            
            
            String[] msg = message.split("\\|");
            ui.addMessage(message);

            if (msg[0].equals("MONITOR")){
                System.out.println("Monitor Connected");
                monitorout = dout;
                monitorin = dis; 
            }
                
            if (msg[0].equals("CLIENT")){

                // client message example: client | client id | request id | 00 | 01 | number of iterations | 0 |
                System.out.println("new client request");
                
                boolean serverNotCrashed = false;
                while (!serverNotCrashed){
                    
                    int serverId;
                    int serverport;
                    
                    try{
                        
                        //ASK MONITOR FOR ONLINE SERVERS
                        String monitorResponse[] =  askServersStatus();
                        
                        //CHOOSE SERVER
                        Integer serverinfo[] = chooseServer(monitorResponse);
                        serverId = serverinfo[0];
                        serverport = serverinfo[1];

                        
                        if (serverId==-1){
                            System.out.println("Zero servers connected");
                            this.clientSocket.close();
                            return;
                        }
                        
                        //INFORM MONITOR ABOUT CHOSEN SERVER
                        String monitorMessage = "LOADBALANCER|SENT_REQUEST|"+msg[1]+"|"+msg[2]+"|"+serverId+"|"+msg[4]+"|"+msg[5]+"|"+msg[6];
                        rl.lock();
                        monitorout.writeUTF(monitorMessage);
                        monitorout.flush();
                        rl.unlock();
                        
                    }catch(Exception e){
                        System.out.println("Error connecting with monitor");
                        return;
                    }

                    
                    //SENDING REQUEST TO SERVER
                    String serverMessage;
                    try{

                        serverMessage = sendReqToServer(serverId, serverport, msg[5]);
                        serverNotCrashed = true;

                    }catch(Exception e){
                        System.out.println("Server crashed. Choosing another server");
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {}
                        continue;
                    } 
                    
                    String clientResponse = msg[1] + "|" + msg[2] + "|" + serverId + "|" + serverMessage.split("\\|")[1] + "|" + msg[5] + "|" + serverMessage.split("\\|")[2];
                    
                    try{
                        //INFORM MONITOR ABOUT REQUEST RESPONSE
                        rl.lock();
                        String monitorMessage = "LOADBALANCER|RECEIVED_RESPONSE|" + clientResponse;
                        monitorout.writeUTF(monitorMessage);
                        monitorout.flush();
                        rl.unlock();
                    }catch(Exception e){
                        System.out.println("Error connecting with monitor");
                        return;
                    }
                    
                    
                    try{
                        // Client response
                        
                        dout.writeUTF("LOADBALANCER|" + clientResponse);
                        dout.flush();

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
        
        private String[] askServersStatus(){
            
            String monitorResponse[] = {};
            try{
                //ask monitor for servers status
                System.out.println("Asking server status");

                String monitorMessage = "LOADBALANCER|SERVER_INFO";

                rl.lock();
                monitorout.writeUTF(monitorMessage);
                monitorout.flush();
                //receive message from monitor
                //monitor|serverID, serverPort, ActiveReqs|....
                String monitorMsg = monitorin.readUTF().strip();
                monitorResponse = monitorMsg.split("\\|");
                rl.unlock();

                System.out.println("There are " + (monitorResponse.length -1) + " online servers");
                
            }catch(Exception e){}
            
            return monitorResponse;
        }
        
        private Integer[] chooseServer(String monitorResponse[]){
            //Get the server with less active Requests
            int serverId = -1;
            int serverport = -1;
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
            Integer[] toReturn = {serverId, serverport};
            return toReturn;

        }
        
        private String sendReqToServer(int serverId, int serverport, String iterations) throws IOException{
            
            //SEND REQUEST TO SERVER                    
            System.out.println("Sending request to server " + serverId);

            Socket server = new Socket("127.0.0.1",serverport);
            DataOutputStream serverdout = new DataOutputStream(server.getOutputStream());
            String serverMessage = "LOADBALANCER|" + iterations;
            serverdout.writeUTF(serverMessage);
            serverdout.flush();

            // Server response message:  server|02|Constante ou server|03|0 (caso erro
            DataInputStream server_dis=new DataInputStream(server.getInputStream()); 
            String serverResponse=server_dis.readUTF().strip();
            System.out.println("Received: " + serverResponse + " from server " + serverId);
            ui.addMessage(serverResponse);

            server.close();
            
            return serverResponse;
        }

    }
    
}