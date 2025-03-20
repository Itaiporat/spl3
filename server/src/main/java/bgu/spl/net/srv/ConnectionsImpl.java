package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer,BlockingConnectionHandler<T>> connectionMap = new ConcurrentHashMap<>();


    public void connect(int connectionId, BlockingConnectionHandler<T> handler){
        connectionMap.put(connectionId,handler);
    }

    public boolean send(int connectionId, T msg){
        connectionMap.get(connectionId).send(msg);
        return true;
    }

    public void disconnect(int connectionId){
        connectionMap.remove(connectionId);
    }

}