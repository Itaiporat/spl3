package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ClientList {
    private ConcurrentHashMap<String, Integer> clientMap;
    private Connections<byte[]> connections;

    public ClientList(Connections<byte[]> connections){
        clientMap = new ConcurrentHashMap<>();
        this.connections =connections;

    }

    public boolean sendAll(byte[] msg){ 
        for(Integer id : clientMap.values()){
                connections.send(id,msg);
        }
        return true;
    }

    public boolean logIn(String name, int id){
        if (isLoged(name))
            return false;
        
        clientMap.put(name,id);
        return true;
    }

    public boolean isLoged(String name){
        return clientMap.containsKey(name);
    }

    public boolean isLoged(int id){
        return clientMap.containsValue(id);
    }
    public void logOut(String name){
        clientMap.remove(name);
    }
}
