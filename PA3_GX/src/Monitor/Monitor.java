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
import java.util.concurrent.CountDownLatch;
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
    private int lbport = 2999;
    private DataOutputStream lbdout;
    private DataInputStream lbin;
    private Map<Integer, ServerInfo> serverMap = new HashMap<Integer, ServerInfo>();
    private Monitor_GUI ui;
    
    private CountDownLatch LBCountDownLatch = new CountDownLatch(1);   // To prevent monitor from sending messages to lb while not connected
    
    public Monitor(Monitor_GUI ui){
        this.ui = ui;
    }

    public Monitor(){}
    
    public void startServer(int port, int lbport){        
        
        this.lbport = lbport;
        // Starting Server for server to connect
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
        
        
        
        Runnable lbTask = new Runnable() {
            @Override
            public void run() {
                listenToLB();
            }
        };  
        Thread lbThread = new Thread(lbTask);
        lbThread.start();
        
        
    }
               
    
    public void listenToLB(){
        
        boolean connected = false;
        System.out.println("Connecting to LB");
        while (!connected){
            try{       
                Socket lbSocket = new Socket("127.0.0.1",lbport);
                this.lbdout = new DataOutputStream(lbSocket.getOutputStream()); 
                this.lbin = new DataInputStream(lbSocket.getInputStream()); 
                connected = true;
            }catch(Exception e){
                System.err.println("Couldn't connect to lb, trying again in 1 sec");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {}            
            }
        }
        
        try{
            
            lbdout.writeUTF("MONITOR|CONNECTION");
            lbdout.flush();

            while (true){
                String lbMessage = lbin.readUTF().strip();
                String[] msg = lbMessage.split("\\|");
                ui.addLBMessage(lbMessage);

                assert(msg[0].equals("LOADBALANCER"));

                if (msg[1].equals("SERVER_INFO")){     
                    String response = "Monitor";

                    Iterator it = serverMap.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry pair = (Map.Entry)it.next();
                        response += "|" + pair.getValue().toString();
                    }
                    lbdout.writeUTF(response);
                    lbdout.flush();
                }

                else if (msg[1].equals("SENT_REQUEST")){
                    System.out.println(lbMessage);
                    int serverid = Integer.valueOf(msg[4]);
                    int reqId = Integer.valueOf(msg[3]);

                    String request = "";
                    for (int i = 2; i < msg.length; i++){
                        request += msg[i] + "|";
                    }
                    serverMap.get(serverid).newReq(reqId, request);

                }
                else if (msg[1].equals("RECEIVED_RESPONSE")){
                    System.out.println(lbMessage);
                    int serverid = Integer.valueOf(msg[4]);
                    int reqId = Integer.valueOf(msg[3]);

                    String request = "";
                    for (int i = 2; i < msg.length; i++){
                        request += msg[i] + "|";
                    }
                    serverMap.get(serverid).endReq(reqId, request);
                }
            }
              
        }catch(Exception e){}
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
                
                ui.addHeartBeat(message);

                if (msg[0].equals("SERVER")){
                    
                    if (msg[1].equals("ID_REQUEST")){     

                        handleIDReq();
                        startHeartBeatProcess();  
                    }
                }

            } 
            catch(Exception e){
                System.out.println("ERROR IN RUN " + e);
            }  
        }
        
        private void startHeartBeatProcess() throws SocketException, IOException{
            //HeartBeat
            clientSocket.setSoTimeout(1000);    //wait 1 secs for responses
            String responseMsg = "MONITOR|HEARTBEAT";

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
                    assert(msg[2].equals("HEARTBEAT"));

                }catch(Exception e){
                    System.out.println("Server " + this.serverId + " disconnected");
                    ui.addHeartBeat("SERVER|" + this.serverId + "|DISCONNETED");

                    clientSocket.close();
                    
                    serverMap.remove(this.serverId);

                    //informLB
                    //informLB("disconnect", serverId, serverPort);

                    return;
                }                    
            }
        }
        
        private void handleIDReq() throws IOException, InterruptedException{
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
            String responseMsg = "MONITOR|" + serverId + "|" + serverPort;
            dout.writeUTF(responseMsg);  
            dout.flush(); 

            //inform LB
            //informLB("connect", serverId, serverPort);
            
        }
        
        
    }
    
    
    
}
