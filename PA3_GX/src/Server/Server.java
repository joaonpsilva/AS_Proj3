/**
* This package contains the classes and methods to play the Server role.
* Please read the requirements carefully.
* 
**/

package Server;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;


// Client para monitor

// Server para loadbalancer e monitor

class Server{
            
    private Socket monitorSocket;
    private Socket serverSocket;
    private Server_GUI ui;
    private static int serverId;                // server id
    private static int serverport;              // port where the server is running
    private static String avogrado = "6.022141527141592653591415926535914159265359";
    private BlockingQueue<Request> queue = new LinkedBlockingDeque<>(2);

    
    public Server(Server_GUI ui){
        this.ui = ui;
        
    }
    
    // Connect to monitor and LB and listen to monitor heart beats
    public void connect(int port, int threads){
        for (int i = 0; i < threads; i++)
            new ServerThread(i).start();    // start server threads
        int sleepTimer = 1000;
        boolean connected = false;
        try{
            System.out.println("Connecting to monitor");
            while (!connected){
                try {
                    monitorSocket = new Socket("127.0.0.1",port);        // connect to monitor
                    connected = true;
                } catch (IOException e) {
                    System.err.println("Error connecting to Monitor, trying again in " + sleepTimer/1000 + "  seconds");
                    Thread.sleep(sleepTimer);
                }
            }
            System.out.println("Connection with monitor estabelished");

            
            // Send ID request to monitor
            DataOutputStream dout = new DataOutputStream(monitorSocket.getOutputStream());
            String msg = "SERVER|ID_REQUEST";
            dout.writeUTF(msg);  
            dout.flush();  
            
            // Receive ID and port from monitor
            DataInputStream dis=new DataInputStream(monitorSocket.getInputStream());  
            String receivedMessage = dis.readUTF().strip();
            String[] message = receivedMessage.split("\\|");
            System.out.println("Server id: " + message[1]);
            this.serverId = Integer.parseInt(message[1]);
            ui.serverIdLabel.setText("Server id: " + this.serverId);
            this.serverport = Integer.parseInt(message[2]);
            ui.addMonitorMessage(receivedMessage);
            
            // start thread to connect to LB
            ListenLB(this.serverport);
            
            //Receive HeartBeats
            while (true) {
                // receive heart beat
                receivedMessage = dis.readUTF().strip();
                message = receivedMessage.split("\\|");
                assert(message[1].equals("HEARTBEAT"));
                ui.blink();
                
                // respond to heartbeat
                String responseMsg = "SERVER|" + serverId + "|HEARTBEAT";
                dout.writeUTF(msg);  
                dout.flush();  
            }
            
        }catch(Exception e){
            System.err.println("Error");
            System.err.println(e);
        }
    }
    
    
    
    // Listen to LB requests and puts the request in the queue
    // returns request denied if too many in queue
    public void ListenLB(int port) {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    // start server port
                    ServerSocket serverSocket = new ServerSocket(port);
                    System.out.println("Waiting for clients requests");
                    while (true) {  // receive requests and put them in queue
                        Socket client = serverSocket.accept();
                        Request req = new Request(client);
                        req.readReq();
                        
                        System.out.println(req.message);
                        ui.addClientMessage(req.message);
                        
                        boolean availableSlot = queue.offer(req); // check available slot to handle request

                        if (availableSlot==false){
                            System.out.println("Request Denied - to many requests to handle");
                            req.dout.writeUTF("SERVER|DENNIED|0");
                            req.dout.flush();
                        }
                    }
                } 
                catch(IOException e){

                }
            
            }    
        };  
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }
        
    
    // Thread that handles client request and answer them
    // waits for one request to be in the queue, takes the request and returns the answer
    private class ServerThread extends Thread {
        private int id;     // id know which thread to change in the UI
        private ServerThread(int i) {
            System.out.println("Working Thread Initiated");
            this.id = i;
        }
        @Override
        public void run() {
            
            while(true){
                try{
                    Request clientRequest = queue.take();
                    
                    String message = clientRequest.message.split("\\|")[1];
                    
                    int iterations = Integer.parseInt(message);
                    System.out.println("Viewing request. Calculating with iterations: " + iterations);
                    
                    stupidChangeColor(new Color(255,204,204), id);
                    updateUI(id, iterations);

                    String avogradoIteration = avogrado.substring(0, iterations);
                    Thread.sleep(1000 * iterations);
                    stupidChangeColor(new Color(204,255,204), id);

                    String response = "SERVER|ACCEPTED|" + avogradoIteration;
                    
                    System.out.println(response);
                    clientRequest.dout.writeUTF(response);
                    clientRequest.dout.flush();
                    clientRequest.socket.close();

                } 
                catch(Exception e){
                    System.out.println(e);
                }  
            }
        }    
    }
    
    private void stupidChangeColor(Color color, int id){
        
        System.out.println(id);
        if (id == 0){
            ui.thread1Panel.setBackground(color); // light red color
        }
        if (id == 1){
            ui.thread2Panel.setBackground(color); // light red color
        }
        if (id == 2){
            ui.thread3Panel.setBackground(color); // light red color
        }
    }
    
    
    // TODO melhorar isto (meter timers ??)
    private void updateUI(int id, int iterations){
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                
                Color red = new Color(255,204,204);     // For when the thread is running
                Color green = new Color(204,255,204);   // For when the thread is idle
                
                if (id == 0){   // TODO update ids and labels
                    ui.t1ProcessingLabel.setVisible(true);
                    ui.t1CountDownLabel.setVisible(true);
                    
                    ui.t1ProcessingLabel.setText("Calculating for " + iterations + " iterations");
                    
                    for (int i = 0; i < iterations; i++){
                        ui.t1CountDownLabel.setText("Done in " + String.valueOf(iterations - i) + " seconds");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    ui.t1CountDownLabel.setText("Done");
                    ui.t1ProcessingLabel.setText("IDLE - waiting for requests");
                    try {
                    Thread.sleep(1000);
                    if ("Done".equals(ui.t1CountDownLabel.getText()))
                        ui.t1CountDownLabel.setVisible(false);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (id == 1){
                    ui.t2ProcessingLabel.setVisible(true);
                    ui.t2CountDownLabel.setVisible(true);
                    ui.t2ProcessingLabel.setText("Calculating for " + iterations + " iterations");

                    
                    for (int i = 0; i < iterations; i++){
                        ui.t2CountDownLabel.setText("Done in " + String.valueOf(iterations - i) + " seconds");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    ui.t2CountDownLabel.setText("Done");
                    ui.t2ProcessingLabel.setText("IDLE - waiting for requests");
                    try {
                    Thread.sleep(1000);
                    if ("Done".equals(ui.t2CountDownLabel.getText()))
                        ui.t2CountDownLabel.setVisible(false);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (id == 2){
                    ui.t3ProcessingLabel.setVisible(true);
                    ui.t3CountDownLabel.setVisible(true);
                    ui.t3ProcessingLabel.setText("Calculating for " + iterations + " iterations");
                    
                    for (int i = 0; i < iterations; i++){
                        ui.t3CountDownLabel.setText("Done in " + String.valueOf(iterations - i) + " seconds");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    ui.t3CountDownLabel.setText("Done");
                    ui.t3ProcessingLabel.setText("IDLE - waiting for requests");
                    try {
                    Thread.sleep(1000);
                    if ("Done".equals(ui.t3CountDownLabel.getText()))
                        ui.t3CountDownLabel.setVisible(false);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }   
            
            }
        }).start();
        
    }
}