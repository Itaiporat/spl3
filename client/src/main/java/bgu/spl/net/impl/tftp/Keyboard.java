package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class Keyboard extends Thread{

	private Scanner scanner;
    private boolean terminate;
    
    private TftpEncoderDecoder encdec;
    private Listener listeningThread;
    
    public Keyboard(Socket socketConnection, TftpEncoderDecoder encdec, Listener listeningThread) {
        this.scanner = new Scanner(System.in);
        this.encdec = encdec;
        this.listeningThread = listeningThread;
        this.terminate = false;
    }
    
    @Override
    public void run() {
        while (!terminate) {
            	String input = scanner.nextLine();
				byte[] message = encdec.encode(input.getBytes());
				short opcode  = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x0ff);
				boolean validCommand = true;

				if(opcode == encdec.opRRQ){
					validCommand = handleRRQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
				}

				else if(opcode == encdec.opWRQ){
					validCommand = handleWRQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
					
				}
				else if(opcode == encdec.opDIRQ){
					listeningThread.setRRQorDIRQ(encdec.opDIRQ);
				}					
				
				else if(opcode == encdec.opDISC){
					listeningThread.DISC = true;
					listeningThread.send(message);
					while(!listeningThread.shouldTerminate());
					terminate = true;
					System.out.println("user disconnected");
					scanner.close();
				}
				if(validCommand || (opcode != 1 && opcode != 2))
					listeningThread.send(message);
									
		}
    }


	private boolean handleRRQ(String filename){
		File file = new File(filename);
        if (file.exists()) {
			println("File already exists");
			listeningThread.clearQueues();
			return false;
        }
		else{
			listeningThread.setRRQorDIRQ(encdec.opRRQ);
			listeningThread.setFileName(filename);	
			return true;
		}

	}

	private boolean handleWRQ(String filename){
		File file = new File(filename);
		if(!file.exists()){
			println("file does not exists");
			
			listeningThread.clearQueues();
			return false;
		}
		else{
			packData(filename);
			listeningThread.setFileName(filename);
			listeningThread.setWRQ(true);
			return true;
		}

	}

	private void println(String str){
        try{
            synchronized (System.out){
                System.out.println(str);
            }
        }catch(Exception e){}
    }
	private void packData(String filename){
		Path path = Paths.get("").toAbsolutePath().resolve(filename);
        try (FileInputStream fis = new FileInputStream(path.toString())) {
			
            byte[] buffer = new byte[encdec.dataPacketMaxSize];
            int read;
			short blockNum = 0;
            while((read = fis.read(buffer)) != -1) {
                byte[] temp = new byte[read];
                System.arraycopy(buffer, 0, temp, 0, read);
				byte[] dataPack = new byte[temp.length + encdec.dataInfoSize];
				dataPack = addDataInfo(temp, blockNum);
                listeningThread.addDataPackQ(dataPack);
				blockNum++;
            }
			blockNum = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private byte[] addDataInfo(byte[]msg, short blockNum){
		byte[] added = new byte[msg.length + encdec.dataInfoSize];
		System.arraycopy(msg, 0, added, encdec.dataInfoSize, msg.length);
        added[0] = (byte)0;
        added[1] = (byte)encdec.opDATA;
        added[2] = (byte)(((short)((msg.length)>> 8) & 0x0ff));
        added[3] = (byte)((short)msg.length &0xff);
        added[4] =  (byte)( blockNum >> 8); 
        added[5] = (byte) (blockNum & 0x00ff);
        return added;

    }
}