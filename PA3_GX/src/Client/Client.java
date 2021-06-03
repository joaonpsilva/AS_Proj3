/**
* This package contains the classes and methods to play the Client role.
* Please read the requirements carefully.
* 
**/

package Client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;


class Client{
            
    private Socket clientSocket;
    private DataOutputStream dout;
    private int clientId;
    private int reqIncr = 0;
    private Client_GUI clientUI;

    public Client(){
        this.clientId = 6;
    }
    
    public Client(Client_GUI ui){
        this.clientId = 6;
        this.clientUI = ui;
    }
    
    
    public void connect(String address, int port){
        
        boolean connected = false;
        int sleepTimer = 1000;
        System.out.println("Trying to connect to Load Balancer");
        try{
            clientSocket = new Socket(address,port);        // Load balancer port
            dout = new DataOutputStream(clientSocket.getOutputStream());
            System.out.println("Connection initiated");

            // Send message
            String msg = "client|" + this.clientId + "|" + (this.clientId * 1000 + this.reqIncr) + "|00|01|" + 1 + "|0|";
            dout.writeUTF(msg);  
            dout.flush(); 
            clientUI.messageStatusLabel.setVisible(true);

            //Receive message
            DataInputStream dis=new DataInputStream(clientSocket.getInputStream());  
            String  receivedMessage = dis.readUTF().strip();
            System.out.println(receivedMessage);
            clientUI.receivedMessageTextField.setText(receivedMessage);
                
        }catch(ConnectException e){
            System.err.println("Failed to connect to Load Balancer trying again in " + sleepTimer/1000 + " seconds");
            connect(address, port);
            try {
                Thread.sleep(sleepTimer);
            } catch (InterruptedException ex) {
                System.err.println("Thread error");
                System.exit(1);
            }
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
