package bgu.spl.net.impl.tftp;
import java.net.Socket;

public class TftpClient {
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        try(Socket sock = new Socket (args[0], 7777)){
            TftpEncoderDecoder encdec = new TftpEncoderDecoder();
            Listener listener = new Listener(encdec, sock);
            listener.start();
            System.out.println("listener started");
            Keyboard keyboard = new Keyboard(sock, encdec, listener);
            keyboard.start();
            System.out.println("keyboard started");

            try {
                listener.join();
                keyboard.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }
}
