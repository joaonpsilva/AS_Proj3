/**
* This package contains the classes and methods to play the Monitor role.
* Please read the requirements carefully.
* 
**/

package Monitor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

class Monitor{
    
    final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(100);
    private int serverIds = 0;
    private int server_ports = 4000;
    private final ReentrantLock rl = new ReentrantLock( true );
    private final int lbport = 3000;
    private DataOutputStream lbdout;
    private Map<Integer, ServerInfo> serverMap = new HashMap<Integer, ServerInfo>();

    public Monitor(){}
    
    public void startServer(int port){
        
        /*try{
                   
            System.out.println("Connecting to LB");
            Socket lbSocket = new Socket("127.0.0.1",lbport);
            this.lbdout = new DataOutputStream(lbSocket.getOutputStream()); 
        }catch(Exception e){}*/

        
        
        Runnable serverTask = new Runnable() {
        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Waiting for servers to connect...");
                while (true) {
                    Socket client = serverSocket.accept();
                    clientProcessingPool.submit(new Connection(client));
                }
            } 
            catch(IOException e){}
            
            }    
        };  
        
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }
    
    private class Connection implements Runnable {
        private final Socket clientSocket;
        private int serverId;
        private int serverPort;
        private DataInputStream dis;
        private DataOutputStream dout;

        private Connection(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            dis=new DataInputStream(clientSocket.getInputStream()); 
            dout = new DataOutputStream(clientSocket.getOutputStream());
        }

        @Override
        public void run() {
            
            // Rec
            try{

                String  message=dis.readUTF().strip();
                String[] msg = message.split("\\|");

                if (msg[0].equals("Server")){
                    
                    if (msg[1].equals("id_request")){     

                        handleIDReq();
                        startHeartBeatProcess();  
                    }
                }
                else if ( msg[0].equals("LoadBalancer")){
                    
                    if (msg[1].equals("serverInfo")){     
                        String response = "Monitor";
                        
                        Iterator it = serverMap.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry pair = (Map.Entry)it.next();
                            response += "|" + pair.getValue().toString();
                        }
                        dout.writeUTF(response);
                        dout.flush();
                    }
                    
                    else if (msg[1].equals("sentMessage")){
                        
                    }
                    else if (msg[1].equals("ReceivedMessage")){
                        
                    }
                }

            } 
            catch(Exception e){
                System.out.println("ERROR IN RUN " + e);
            }  
        }
        
        /*private void informLB(String state, int serverid, int serverport){
            
            System.out.println("Telling Load Balancer server " + state);
            
            String msg = "monitor|" + state + "|" + serverid + "|" + serverport;
            try{
                lbdout.writeUTF(msg);  
                lbdout.flush();            
            }catch(Exception e){
                System.out.println("ERROR INFORMING LOAD BALANCER");
                System.out.println(e);
            }

        }*/
        
        private void startHeartBeatProcess() throws SocketException, IOException{
            //HeartBeat
            clientSocket.setSoTimeout(1000);    //wait 1 secs for responses
            String responseMsg = "Monitor|HeartBeat";

            while (true){
                try{

                    //send
                    Thread.sleep(1000); //1 sec
                    dout.writeUTF(responseMsg);  
                    dout.flush(); 

                    //receive
                    String beatResponse = dis.readUTF().strip();
                    String[] msg = beatResponse.split("\\|");
                    assert(msg[1].equals(serverId));
                    assert(msg[2].equals("HeartBeat"));

                }catch(Exception e){
                    System.out.println("Server " + this.serverId + " disconnected");

                    clientSocket.close();
                    
                    serverMap.remove(this.serverId);

                    //informLB
                    //informLB("disconnect", serverId, serverPort);

                    return;
                }                    
            }
        }
        
        private void handleIDReq() throws IOException{
            //Get next Id For new server
            try {
                // garantir acesso em exclusividade
                rl.lock();
                this.serverId = serverIds;
                this.serverPort = server_ports;
                serverIds++;
                server_ports++;
            } catch ( Exception ex ) {}
            finally {
                rl.unlock();
            }

            System.out.println("New server connection. Giving id: " + this.serverId );
            serverMap.put(serverId, new ServerInfo(serverId, serverPort));

            
            //respond to server
            String responseMsg = "Monitor|" + serverId + "|" + serverPort;
            dout.writeUTF(responseMsg);  
            dout.flush(); 

            //inform LB
            //informLB("connect", serverId, serverPort);
            
        }
        
        
    }
    
    
    
}
