/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author joaonps
 */
public class ServerInfo implements Comparable<ServerInfo>{
    
    private int serverId;
    private int serverPort;
    private Integer activeRequests;
    private Map<Integer, String> requests = new HashMap<>();


    public ServerInfo(int serverId, int serverPort) {
        this.serverId = serverId;
        this.serverPort = serverPort;
        this.activeRequests = 0;
    }
    
    public ServerInfo(int serverId, int serverPort, int activeRequests) {
        this.serverId = serverId;
        this.serverPort = serverPort;
        this.activeRequests = activeRequests;
    }
    
    public int getServerId() {
        return serverId;
    }

    public int getServerPort() {
        return serverPort;
    }
    
    public String toString(){
        return this.serverId + "," + this.serverPort + "," + this.getActiveRequests();
    }
    
    public void newReq(int requestId, String request){
        this.requests.put(requestId, request);
        this.activeRequests++;
    }
    
    public void endReq(int requestId, String request){
        this.requests.remove(requestId);
        this.activeRequests--;
    }


    public Integer getActiveRequests() {
        return activeRequests;
    }
    

    @Override
    public int compareTo(ServerInfo other) {
        return getActiveRequests().compareTo(other.getActiveRequests());
    }
    
    
    
    
    
         
}
