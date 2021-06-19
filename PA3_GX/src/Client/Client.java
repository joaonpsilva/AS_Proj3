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
    public int clientId;
    private int reqIncr = 0;
    private Client_GUI clientUI;
    private DataOutputStream dout;
    private DataInputStream dis;

    public Client(){}
    
    public Client(Client_GUI ui){
        this.clientUI = ui;
    }
    
    public void setClientId(int id){
        this.clientId = id;
    }
    
    public void connect(String address, int port){
        
        int sleepTimer = 1000;
        System.out.println("Trying to connect to Load Balancer");
        try{
            clientSocket = new Socket(address,port);        // Load balancer port
            dout = new DataOutputStream(clientSocket.getOutputStream());
            dis=new DataInputStream(clientSocket.getInputStream());  
            System.out.println("Connection initiated");
            
        }catch(Exception e){
            System.err.println("Failed to connect to Load Balancer trying again in " + sleepTimer/1000 + " seconds");
            connect(address, port);
            try {
                Thread.sleep(sleepTimer);
            } catch (InterruptedException ex) {
                System.err.println("Thread error");
                System.exit(1);
            }
        }
    }
    
    public void sendRequest(String address, int port){
        
        connect(address, port);
        
        try{
            // Send message
            String msg = "CLIENT|" + this.clientId + "|" + (this.clientId * 1000 + this.reqIncr) + "|00|01|" + clientUI.incrementationsTextField.getText() + "|0|";
            this.reqIncr++;
            
            dout.writeUTF(msg);  
            dout.flush(); 
            clientUI.messageStatusLabel.setVisible(true);

            //Receive message
            String  receivedMessage = dis.readUTF().strip();
            System.out.println(receivedMessage);
            clientUI.receivedMessageTextField.setText(receivedMessage);
                
        }catch(Exception e){
            Logger.getLogger("ERROR");
        }
    }
}
