package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;


public class Listener extends Thread {

    private TftpEncoderDecoder encdec;
    private Socket sock;
    //private ConcurrentLinkedQueue<Short> commandQueue  = new ConcurrentLinkedQueue<Short>();
    //private ConcurrentLinkedQueue<String> fileNameQueue = new ConcurrentLinkedQueue<String>();
    private boolean terminate = false;
    private ConcurrentLinkedQueue<byte[]> dataPackQ = new ConcurrentLinkedQueue <>();
    private BufferedOutputStream output;
    public boolean lastBlockNum;
    private short RRQorDIRQ;
    private String fileName;
    public boolean DISC = false;
    private boolean WRQ = false;
    public Listener(TftpEncoderDecoder encdec, Socket sock){
        this.encdec = encdec;
        this.sock = sock; 
        try{
            this.output = new BufferedOutputStream(sock.getOutputStream());
        }catch(IOException e){
            e.printStackTrace();
        }
       
    }

    public void run(){
        try{
        BufferedInputStream inputStream = new BufferedInputStream(sock.getInputStream());
        this.output = new BufferedOutputStream(sock.getOutputStream());
        int nextByte;
        
            while(!shouldTerminate() && (nextByte = inputStream.read()) >= 0){
                byte[] msg = encdec.decodeNextByte((byte)nextByte);
                if(msg != null){
                    short opcode = (short)(((short) msg[0]) << 8 | (short) (msg[1]) & 0xFF);

                    if(opcode == encdec.opDATA)
                        DATA(msg);

                    else if(opcode == encdec.opACk)
                        ACK(msg);

                    else if(opcode == encdec.opERROR)
                        Error(msg);

                    else if(opcode == encdec.opBCAST)
                        BCAST(msg);

                    else{
                        System.out.println("unknown error");
                    }
                }         
        }
    }catch(IOException e){
            e.printStackTrace();
        }
    }

    public boolean shouldTerminate() {
        return this.terminate;
    }

    private void DATA(byte[] msg){
                if(RRQorDIRQ == encdec.opRRQ){
                    writeToFile(msg, fileName);
                    send(new byte[] {(byte)0,(byte)4,msg[4],msg[5]});
    
                    if(msg.length < encdec.dataPacketMaxSize){
                        RRQorDIRQ = -1;
                        println("RRQ " + fileName + " complete");
                        fileName = null;
                    }          
                }
    
                else if(RRQorDIRQ == encdec.opDIRQ){//DIRQ pack
                    ByteBuffer buf = ByteBuffer.allocate(msg.length-encdec.dataInfoSize);
                    buf.put(msg,encdec.dataInfoSize, msg.length-encdec.dataInfoSize);
                    buf.flip();
                    while(buf.hasRemaining()){
                        String str = new String(msg, StandardCharsets.UTF_8);
                        String[] files = str.split("\0");
                        
                        try{
                            synchronized(System.out){
                                for(String file : files){
                                    System.out.println(file);
                                }
                            }
                        }catch(Exception e){} 
                        buf.compact();
                    }               
                    RRQorDIRQ = -1;
                    buf.clear();        
                }
                else{
                    System.out.println("unknown data");
                }
                    
    }


    private void ACK(byte[] msg){
        short blockNum = (short)((short)( msg[2]) << 8| (short)(msg[3]) & 0xff);
        println("ACK " + blockNum);
        if(blockNum == 0 && !WRQ){
            if(DISC == true){
                this.terminate = true;
            }
        }
         else{//WRQ command
            if(lastBlockNum){
                lastBlockNum = false;
                println("WRQ " + fileName +" completed" );
                fileName = null;
                WRQ = false;
                
            }
            else{
                if(dataPackQ.peek().length<encdec.dataPacketMaxSize){
                    lastBlockNum = true;
                }
                send(dataPackQ.poll());
            } 
         }   

    }


    public void setWRQ(boolean wrq) {
        WRQ = wrq;
    }

    private void BCAST(byte[] msg){
        String addOrDel;
        if(msg[2] == (byte)1)
            addOrDel = "add ";
        
        else   
            addOrDel = "del ";

            String str = new String (msg, 3, msg.length - 3, StandardCharsets.UTF_8);
            println(addOrDel + str);                 
    }

    public void Error(byte[] msg){
        short errorCode = (short) (((short) msg[2]) << 8 | (short) (msg[3]) & 0x00ff);
        String str = new String (msg, 4, msg.length - 4, StandardCharsets.UTF_8);
        println("Error " + errorCode + " " + str);
        clearQueues();
        
    }

    public void setFileName(String fileName){
        this.fileName = fileName ;
    }

    public void send(byte[] msg){
        if (msg!=null){
            try{ 
                    output.write(msg);
                    output.flush();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        
    }

    private void  println(String str){
        try{
            synchronized (System.out){
                System.out.println(str);
            }
        }catch(Exception e){}
    }
    public void addDataPackQ(byte[] dataPack){
        dataPackQ.add(dataPack);
    }


    private void writeToFile(byte[] msg, String fileName){
        short packSize = TwoByteToShort(msg, 2);
        ByteBuffer buf = ByteBuffer.allocate(packSize);
        Path path = Paths.get(fileName);
            buf.put(msg,(short)6,packSize);
            buf.flip();

                try(FileOutputStream fos = new FileOutputStream(path.toString(),true)){
                    while(buf.hasRemaining())
                        fos.write(buf.get());
    
                } catch (IOException e) {
                    e.printStackTrace();
                }
               
            buf.clear();
            
    }

    private short TwoByteToShort(byte[] message, int i){
        return (short)((((short)message[i] & 0xFF) << 8) | ((short)message[i+1] & 0xFF));
    }

    public void clearQueues(){
        fileName = null;
        WRQ = false;
        DISC = false;
        RRQorDIRQ = -1;
        if(!dataPackQ.isEmpty())
            dataPackQ.clear();
        println("error: all commands were cleared, please enter any unhandled commands");
    }

    public void setRRQorDIRQ(short op) {
        RRQorDIRQ = op;
    }
    
}

