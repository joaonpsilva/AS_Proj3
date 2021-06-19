/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author joaonps
 */
public class Request {
    
    public Socket socket;
    public String message;
    public DataInputStream dis; 
    public DataOutputStream dout;


    public Request(Socket socket) {
        this.socket = socket;   
        try { 
            this.dis = new DataInputStream(socket.getInputStream());
            this.dout = new DataOutputStream(socket.getOutputStream());
        } catch (IOException ex) {
            Logger.getLogger(Request.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public String readReq(){
        try {
            this.message = dis.readUTF().strip();
        } catch (IOException ex) {
            Logger.getLogger(Request.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return this.message;
    }
    
    
}
