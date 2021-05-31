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
    
    private CountDownLatch LBCountDownLatch = new CountDownLatch(1);   // To prevent monitor from sending messages to lb while not connected
    
    public Monitor(){}
    
    public void startServer(int port){

        // Starting Server for server to connect
        Runnable serverTask = new Runnable() {
        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Waiting for servers to connect...");
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("Connection with server estabelished: " + client);
                    clientProcessingPool.submit(new ServerConnection(client));
                }
            } 
            catch(IOException e){}
            
            }    
        };  
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
        
        
        // Connecting to LB
        boolean connectedToLB = false;
        int sleepTimer = 2000;        
        System.out.println("Trying to connect to LB");
        while (!connectedToLB) {
            try{       
                Socket lbSocket = new Socket("127.0.0.1",lbport);
                this.lbdout = new DataOutputStream(lbSocket.getOutputStream());
                connectedToLB = true;
            }catch(Exception e){
                System.err.println("Error connecting to LB, trying again in " + sleepTimer/1000 + "  seconds");
                try {
                    Thread.sleep(sleepTimer);
                } catch (InterruptedException ex) {
                    System.err.println("Thread error");
                    System.exit(0);
                }
            }
        }
        LBCountDownLatch.countDown();
        System.out.println("Connected to LB");
        
    }
    
    private class ServerConnection implements Runnable {
        private final Socket clientSocket;
        private int serverId;
        private int serverPort;
        private DataInputStream dis;
        private DataOutputStream dout;

        private ServerConnection(Socket clientSocket) throws IOException {
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


                if (msg[1].equals("id_request")){     

                    handleIDReq();
                    startHeartBeatProcess();  
                }

            } 
            catch(Exception e){
                System.out.println("ERROR IN RUN " + e);
            }  
        }
        
        // Inform LB when a connected server changes state (connected/disconnected)
        private void informLB(String state, int serverid, int serverport) throws InterruptedException{
            LBCountDownLatch.await();
            System.out.println("Telling Load Balancer server " + state);
            String msg = "monitor|" + state + "|" + serverid + "|" + serverport;
            try{
                lbdout.writeUTF(msg);  
                lbdout.flush();            
            }catch(Exception e){
                System.out.println("ERROR INFORMING LOAD BALANCER");
                System.err.println(e);
            }

        }
        
        private void startHeartBeatProcess() throws SocketException, IOException, InterruptedException{
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

                    //informLB
                    informLB("disconnect", serverId, serverPort);

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

            //respond to server
            String responseMsg = "Monitor|" + serverId + "|" + serverPort;
            dout.writeUTF(responseMsg);  
            dout.flush(); 

            //inform LB
            informLB("connect", serverId, serverPort);
        }
        
        
    }
    
    
    
}
