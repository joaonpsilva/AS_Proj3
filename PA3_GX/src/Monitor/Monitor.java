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
    
    public Monitor(){}
    
    public void startServer(int port) throws IOException{
        
        System.out.println("Connecting to LB");
        Socket lbSocket = new Socket("127.0.0.1",lbport);
        this.lbdout = new DataOutputStream(lbSocket.getOutputStream());
        
        
        Runnable serverTask = new Runnable() {
        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Waiting for servers to connect...");
                while (true) {
                    Socket client = serverSocket.accept();
                    clientProcessingPool.submit(new ServerConnection(client));
                }
            } 
            catch(IOException e){}
            
            }    
        };  
        
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }
    
    private class ServerConnection implements Runnable {
        private final Socket clientSocket;
        private int serverId;
        private int serverPort;
        private DataInputStream dis;
        private DataOutputStream dout;

        private ServerConnection(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            DataInputStream dis=new DataInputStream(clientSocket.getInputStream()); 
            DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
        }

        @Override
        public void run() {
            
            // Rec
            try{

                String  message=dis.readUTF().strip();
                String[] msg = message.split("|");
                
                if (msg[1].equals("id_request")){       
                    handleIDReq();
                    startHeartBeatProcess();  
                }

            } 
            catch(Exception e){
                System.out.println(e);
            }  
        }
        
        private void informLB(String state, int serverid, int serverport) throws IOException{
            
            System.out.println("Telling Load Balancer server connected/disconnected");
            
            String msg = "monitor|" + state + "|" + serverid + "|" + serverport;
            lbdout.writeUTF(msg);  
            lbdout.flush();  
        }
        
        private void startHeartBeatProcess() throws SocketException, InterruptedException, IOException{
            //HeartBeat
            clientSocket.setSoTimeout(2000);    //wait 2 secs for responses
            while (true){

                //send
                Thread.sleep(1000); //1 sec
                String responseMsg = "Monitor|HeartBeat";
                dout.writeUTF(responseMsg);  
                dout.flush(); 

                //receive
                try{
                    String beatResponse = dis.readUTF().strip();
                    String[] msg = beatResponse.split("|");
                    assert(msg[1].equals(serverId));
                    assert(msg[2].equals("HeartBeat"));

                }catch(SocketTimeoutException e){

                    System.out.println("Server " + this.serverId + " disconnected");

                    clientSocket.close();

                    //informLB
                    informLB("disconnect", serverId, serverPort);

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
