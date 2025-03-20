package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.ClientList;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;



public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;
    private Connections<byte[]> connections;
    private int connectionId;
    private String username;
    private short opcode;
    private String fileName;
    private ByteBuffer buffer; 
    private List<byte[]> dataPackQ = new LinkedList <>();
    private static final Map<Path, ReadWriteLock> locks = new HashMap<>();
    private ClientList clients;
    private Path path;

    private final short opRRQ = 1;
    private final short opWRQ = 2;
    private final short opDATA = 3;
    private final short opACk = 4;
    private final short opERROR = 5;
    private final short opDIRQ = 6;
    private final short opLOGRQ = 7;
    private final short opDELRQ = 8;
    private final short opBCAST = 9;
    private final short opDISC = 10;
    private final short dataPacketMaxSize = 512;
    private final short dataInfoSize = 6;
    private final boolean ADD = true;
    private final boolean DELETE = false;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections =connections;
        this.clients = new ClientList(connections);
        this.buffer = ByteBuffer.allocate(512);
    }

    @Override
    public void process(byte[] message) {
        opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);//initiate opcode

        if(opcode == opLOGRQ){
            LOGRQ(message);
        }
        else if(clients.isLoged(connectionId)){
            if(opcode == opRRQ){
                if(validConnection())   
                    RRQ(message);
            }
    
            else if(opcode == opWRQ){
                if(validConnection())
                    WRQ(message);
            }
    
            else if(opcode == opDATA){
                DATA(message);
            }
    
            else if(opcode == opACk){
                ACK(message);
            }
    
            else if(opcode == opDIRQ){
                if(validConnection()){
                    DIRQ();
                }
            }
    
            else if(opcode == opDELRQ)
                DELRQ(message);
    
            else if(opcode == opDISC){
                if(validConnection()){
                    DISC();
                }
            }
    
            else{
                ERROR((short)4);
            }
    
        }
        else{
           ERROR ((short)6);
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    private void RRQ(byte[] message){
        fileName = new String(message, 2, message.length-2, StandardCharsets.UTF_8);
        
        path = Paths.get("./Flies/", fileName);
        if(!Files.exists(path))
                ERROR((short)1);
                
        else{
            packData();
            
            //sending the first pack
            ACK(initiateAck(0));
        }   
        fileName = null;
        path = null;

    }

    private void WRQ(byte[] message){
        fileName =  new String(message, 2, message.length-2, StandardCharsets.UTF_8);
        path = Paths.get("./Flies/", fileName);

        if(Files.exists(path)){
            ERROR((short)5);
            
        }
        else{
            try{
                byte[] ack = new byte[4];
                ack = initiateAck(0);
                connections.send(connectionId,ack);
            }
             catch(Exception e){
                  e.printStackTrace();
            }
        }

    }

    private void DATA(byte[] message){
        byte[] ack = new byte[4];
        
        try{
            short blockNum = TwoByteToShort(message, 4);
            short packSize = TwoByteToShort(message, 2);

            buffer.put(message, (short)6, packSize);
            ReadWriteLock lock = getLockForPath(path);

            while(!lock.writeLock().tryLock());
            try (FileOutputStream fos = new FileOutputStream(path.toString(),true)) {
                buffer.flip();

                while (buffer.hasRemaining()) {
                    fos.write(buffer.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally{
                lock.writeLock().unlock();
            }
            
            buffer.clear();


            ack = initiateAck(blockNum);
            ACK(ack);

            if(packSize < 512){//this is the last data pack to send
                //Path path = Paths.get("./Flies/" + fileName);
                //Files.createFile(path);
                connections.send(connectionId, ack);
                BCAST(fileName, ADD);
                fileName = null;
                path = null;
            }

            else{
                connections.send(connectionId, ack);
            }
        }catch(Exception e){}

    }

    private void ACK(byte[] message){

        if(!dataPackQ.isEmpty()){
            byte[] block = dataPackQ.get(0);
            byte[] dataPack = new byte[block.length + dataInfoSize];
            dataPack[0] = (byte)0;
            dataPack[1] = opDATA;
            System.arraycopy(shortToByte((short)(dataPack.length-dataInfoSize)), 0, dataPack,2 , 2);
            System.arraycopy(message,2 , dataPack,4 , 2);
            System.arraycopy(block,0,dataPack,dataInfoSize,block.length);
            dataPackQ.remove(0);
            connections.send(connectionId, dataPack);     
        }
    }

    private void ERROR(short errCode){
        byte[] errInfo = { 0, opERROR, ( byte )( errCode >> 8) , ( byte ) ( errCode & 0xff ) };
        String errMsg = null;

        if(errCode == 0)
            errMsg = ("Not defined, see error message (if any).");
        else if(errCode == 1)
            errMsg = ("File not found - RRQ DELRQ of non-existing file");
        else if(errCode == 2)
            errMsg = ("Access violation - File cannot be written, read or deleted.");
        else if(errCode == 3)
            errMsg = ("Disk full or allocation exceeded - No room in disk.");
        else if(errCode == 4)
            errMsg = ("Illegal TFTP operation - Unknown Opcode.");
        else if(errCode == 5)
            errMsg = ("File already exists - File name exists on WRQ.");
        else if(errCode == 6)
            errMsg = ("User not logged in - Any opcode received before Login completes.");
        else if(errCode == 7)
            errMsg = ("User already logged in - Login username already connected.");
            
        byte[] bError = errMsg.getBytes();
        int packSize = errInfo.length + bError.length;
        byte[] pack = new byte[packSize];
        System.arraycopy(errInfo, 0, pack, 0, errInfo.length);
        System.arraycopy(bError, 0, pack, errInfo.length, bError.length);
        connections.send(connectionId, pack);
    }

    public void DIRQ(){
        File folder = new File("./Flies");
        try{
            File[] filesArray = folder.listFiles();
            LinkedList<Byte> filesNameToBytes = new LinkedList<>();
            for (File file: filesArray) 
            {
                String name = file.getName();
                for(byte byt : name.getBytes()) 
                {
                    filesNameToBytes.add(byt); // add each byte in the name
                }
                filesNameToBytes.add((byte)0x00); // at the end of each name add a "buffer"
            }
            int size = filesNameToBytes.size();
            byte[] sendArray = new byte[size + 6]; // we should add at the begining specific values.
            sendArray[0] = 0;
            sendArray[1] = 3;
            sendArray[2] = (byte)((size >>8) & 0xFF);
            sendArray[3] = (byte)(size & 0xFF);
            sendArray[4] = 0;
            sendArray[5] = 1;
            // now we should feel the rest of the cells with the filesNameToBytes list.
            int index = 0;
            for (byte byt: filesNameToBytes)
            {
                sendArray[index+6] = byt; // we add 6 to match the corresponding position between the 2 structures 
                index++;
            }
            this.connections.send(connectionId, sendArray);
        }
        finally{
            // no need to write here nor to creat a catch
        }

    }

    // as explained in the assignment, this method log in the user to the server. 
    // by doing so we will recive a "msg" we will try to log in the user if the msg is right
    // if the user is loged in send a confermation, else we will send an error instead
    private void LOGRQ(byte[] msg){
        // copying the msg without first 2 cells.
        byte[] filesNameToBytes = new byte[msg.length-2]; // user
        for(int i = 2; i<msg.length;i++)
        {
            filesNameToBytes[i-2] = msg[i];
        }
        String strName = new String(filesNameToBytes, StandardCharsets.UTF_8);
        if(clients.logIn(strName, connectionId)){
            byte[] message = {0,opACk,0,0}; // "code"
            username = strName;
            this.connections.send(connectionId,message);
        }
        else{
            ERROR((short)7);
        }
    }

    // as explained in the assignment, this method delete a file from the server.
    // by doing so we need to locate the file name, then trying to delete it using the path.
    // true-> broad cast to all loged in users to notify. else-> error msg.
    private void DELRQ(byte[] message){
        // same as last function, copying the msg without first 2 cells.
        byte[] filesNameToBytes = new byte[message.length-2];
        for(int i = 2; i<message.length;i++)
        {
            filesNameToBytes[i-2] = message[i];
        }
        
        // finding the path using the file name 
        String strName = new String(filesNameToBytes, StandardCharsets.UTF_8);
        Path filesPath = Paths.get("./Flies/", strName);
        ReadWriteLock lock = getLockForPath(filesPath);
        while(!lock.writeLock().tryLock());
        try {
            // Deleting the file using path
            Files.delete(filesPath);
            BCAST(strName, DELETE);
            
        } 
        catch (IOException e) {
            ERROR((short)1);
            System.err.println("Error deleting the file: " + e.getMessage());;
        }finally{
            lock.writeLock().unlock();
        }
    }

    // as explained in the assignment, this method notifying when a file was deleted or added.
    private void BCAST(String name, boolean addOrDelete){
        byte[] nameInBytes = name.getBytes();
        byte[] pack = new byte[nameInBytes.length + 4];
        pack[0] = 0;
        pack[1] = opBCAST;
        pack[2] = 0;
        if(addOrDelete)
            pack[2] = (byte)1;
        pack[pack.length-1] = 0;

        for(int i =0;i < nameInBytes.length;i++)
            pack[i+3] = nameInBytes[i]; // we add 3 since we wrote 3 bytes at the begining 1 at the end. (total 4)
            
        clients.sendAll(pack);
    }

    private void DISC(){
        if(clients.isLoged(username))
            clients.logOut(username);
            
        connections.send(connectionId, initiateAck(0));
    }


    private  boolean validConnection(){
        if(clients.isLoged(connectionId))
            return true;
        
        ERROR((short) 6);
        return false;
    }

    private void packData(){
        ReadWriteLock lock = getLockForPath(path);
        while(!lock.readLock().tryLock());
        try (FileInputStream fis = new FileInputStream(this.path.toString())) {
            byte[] buffer = new byte[dataPacketMaxSize];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                byte[] dataPack = new byte[read];
                System.arraycopy(buffer, 0, dataPack, 0, read);
                dataPackQ.add(dataPack);
            }
           lock.readLock().unlock();
        } catch (Exception e) {
            e.printStackTrace();
        }
        path = null;
    }

    private byte[] shortToByte(short num){
        byte[] byteArray = new byte[2];
        byteArray[0] = (byte) ((num >> 8) & 0xFF);
        byteArray[1] = (byte) (num & 0xFF);
        return byteArray;
    }
    
    private byte[] initiateAck(int ackMsg){
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = (short)4;

        System.arraycopy(shortToByte((short)ackMsg), 0, ack, 2, 2);
        System.out.println(new String(ack));

        return ack;
    }

    private short TwoByteToShort(byte[] message, int i){
        return (short)((((short)message[i] & 0xFF) << 8) | ((short)message[i+1] & 0xFF));
    }

    private synchronized ReadWriteLock getLockForPath(Path path) {
        ReadWriteLock lock = locks.get(path);
        if (lock == null) {
            lock = new ReentrantReadWriteLock();
            locks.put(path, lock);
        }
        return lock;
    }
}
