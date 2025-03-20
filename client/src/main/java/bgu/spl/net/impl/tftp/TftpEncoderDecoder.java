package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    // TODO: Implement here the TFTP encoder and decoder

    private byte[] bytes = new byte[0];
    private int opcode = -1;
    private int dataLength = Integer.MAX_VALUE;
    private final short nullvalue = -1;
    private final short MAX_CMD_SIZE = 5;

    public final short opRRQ = 1;
    public final short opWRQ = 2;
    public final short opDATA = 3;
    public final short opACk = 4;
    public final short opERROR = 5;
    public final short opDIRQ = 6;
    public final short opLOGRQ = 7;
    public final short opDELRQ = 8;
    public final short opBCAST = 9;
    public final short opDISC = 10;
    public final short dataPacketMaxSize = 512;
    public final short dataInfoSize = 6;

    @Override
    public byte[] encode(byte[] message) {

        byte[] bCmd = new byte[MAX_CMD_SIZE];
        int cmdLength = 0;

        while (cmdLength < message.length && cmdLength < bCmd.length && message[cmdLength] != (byte) (' ')) {
            bCmd[cmdLength] = message[cmdLength++];
        }

        encodeOpcode(new String(bCmd, 0, cmdLength, StandardCharsets.UTF_8));

        byte[] bOpcode = new byte[] {(byte) ( opcode >> 8) , ( byte ) ( opcode & 0xff )};
        if (opcode == opRRQ || opcode == opWRQ || opcode == opLOGRQ || opcode == opDELRQ){
            
            byte[] msg = new byte[bOpcode.length + message.length - cmdLength];
            msg[0] = bOpcode[0];
            msg[1] = bOpcode[1];

            System.arraycopy(message, cmdLength + 1, msg, bOpcode.length, message.length - cmdLength - 1);
            msg[msg.length-1] = 0;
            return msg;
        }
        return bOpcode;
    }


    @Override
    public byte[] decodeNextByte(byte nextByte){
        if (bytes.length >= dataLength && nextByte == 0x0) {
            return finish();
        }

        else {
            bytes = addByte(nextByte);

            if (bytes.length == 2) {
                opcode = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
                switch (opcode) {
                    case opRRQ: case opWRQ: case opDIRQ: case opLOGRQ:
                    case opDELRQ: case opDISC:
                        dataLength = 2;
                        break;
                    case opBCAST:
                        dataLength = 3;
                        break;
                    case opACk: case opERROR:
                        dataLength = 4;
                        break;
                    case opDATA:
                        dataLength = dataInfoSize;
                        break;
                    default:
                        dataLength = Integer.MAX_VALUE;
                        break;
                }
            }

            if (opcode == opDATA && bytes.length == 4) {
                int packSize = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
                dataLength = dataInfoSize + packSize;
            }

            if (!lastByteZero()  && bytes.length == dataLength) {
                return finish();
            }
            return null;
        }
    }

private void encodeOpcode(String cmd){

    switch (cmd) {
        case "RRQ":
            opcode = 1;
            break;

        case "WRQ":
            opcode = 2;
            break;

        case "DIRQ":
        opcode = 6;
            break;

        case "LOGRQ":
        opcode = 7;
            break;

        case "DELRQ":
        opcode = 8;
            break;

        case "DISC":
        opcode = 10;
            break;

        default:
        opcode = nullvalue;
            break;
    }
}
 private byte[] finish(){
    byte[] finishMsg = bytes.clone();
    bytes = new byte[0];
    opcode = nullvalue;
    return finishMsg;
 }

 private byte[] addByte(byte nextByte){
    byte[] add = new byte[bytes.length + 1];
    System.arraycopy(bytes, 0, add, 0, bytes.length);
    add[add.length-1] = nextByte;
    return add;
 }

 private boolean lastByteZero(){
    return (opcode == opRRQ || opcode == opWRQ || opcode == opERROR 
    || opcode == opLOGRQ ||  opcode == opDELRQ || opcode == opBCAST);
}
        
}