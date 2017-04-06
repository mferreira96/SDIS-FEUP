package network;

import file.Chunk;
import file.ChunkID;
import logic.*;
import management.FileManager;
import protocols.PutChunk;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.*;

import static management.FileManager.*;


public class MulticastChannelWrapper implements Runnable{

    private MulticastSocket multicastSocket;
    private int port;
    private InetAddress address;
    private ChannelType type;
    private volatile boolean running; //thread safe variable
    private Vector<Observer> observers;


    public MulticastChannelWrapper(String address, String port,ChannelType type) throws IOException {
        this.type = type;
        this.observers = new Vector<>();
        this.running=true;

        //join multicast group
        this.port = Integer.parseInt(port);
        this.address = InetAddress.getByName(address);
        //join multicast group
        multicastSocket = new MulticastSocket(this.port);
        multicastSocket.joinGroup(this.address);

    }

    public void terminateLoop(){
        this.running=false;
    }

    public void closeSocket() throws IOException {
        multicastSocket.leaveGroup(address);
        multicastSocket.close();
    }

    public MulticastSocket getMulticastSocket() {
        return multicastSocket;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return address;
    }

    @Override
    public void run() {

        while(this.running){
            try {
                //receive message
                //todo check the length
                byte[] receiveData = new byte[64*1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                multicastSocket.receive(receivePacket);


                Message msg = new Message(Arrays.copyOf(receivePacket.getData(),receivePacket.getLength()));

                for (Observer obs: observers) {//notify observers's
                    obs.update(msg);
                }

                handleReceivedMessage(msg,receivePacket.getAddress());


            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addObserver(Observer obs){
        observers.add(obs);
    }

    public void removeObserver(Observer obs){
        observers.remove(obs);
    }


    public void handleReceivedMessage(Message msg,InetAddress senderAddress) {


        ChunkID chunkId= new ChunkID(msg.getFileId(), msg.getChunkNo());

        switch (msg.getType()){
            case PUTCHUNK:
                if(!Utils.metadata.isMyFile(msg.getFileId())){

                    System.out.println("[RECEIVED PUTCHUNK] Peer occupied space( " + getSizeOfBackupFolder() + ") fileId( " + msg.getFileId() + ") ,chunkNo(" + msg.getChunkNo() + ")");


                    //Enhancement 1 - Ensure the desired Replication Degree
                    Observer obs= new Observer(Utils.mc);



                    boolean hasChunk=hasChunk(chunkId);

                    if(((getSizeOfBackupFolder()+msg.getMessageBody().length) <= Utils.metadata.getMaximumDiskSpace()) && !hasChunk) {//check if storing the chunk will not overflow the backup space
                            Chunk chunk = new Chunk(msg.getFileId(),msg.getChunkNo(),msg.getMessageBody());
                            saveChunk(chunk);

                            //with a thread
                            //Thread t = new Thread(() -> saveChunk(chunk));
                            //t.start();
                            //--------------

                            Utils.metadata.addChunk(chunkId, msg.getReplicationDeg());
                            hasChunk=true;
                    }else if(!hasChunk)
                        System.out.println("-----------------Exceeded Allowed Backup Space-----------------" +
                                           "\nCan't store chunk(" + chunkId.getChunkID() + ") of the file (" + chunkId.getFileID() + ")");


                    if(hasChunk){
                        Utils.sleepRandomTime(800);
                        obs.stop();

                        int perceivedDegree=obs.getMessageNumber(MessageType.STORED, chunkId.getFileID(), chunkId.getChunkID());
                        System.out.println("PUTCHUNK PERCEIVED DEGREE-> " + perceivedDegree);
                        if (perceivedDegree >= msg.getReplicationDeg()) {
                            Utils.metadata.removeChunk(chunkId);
                            FileManager.deleteChunk(chunkId);
                        }else {
                            Message response = new Message(MessageType.STORED, Utils.version, Utils.peerID, msg.getFileId(), msg.getChunkNo());
                            Utils.sleepRandomTime(400);
                            response.send(Utils.mc);
                        }
                    }
                }

                break;
            case GETCHUNK:

                if(hasChunk(chunkId)){
                    String version = msg.getVersion();
                    boolean withEnhancement=false;

                    if(!version.equals("1.0"))
                        withEnhancement=true;


                    Observer obs = new Observer(Utils.mdr);
                    Utils.sleepRandomTime(400);
                    obs.stop();

                    if(obs.getMessage(MessageType.CHUNK,msg.getFileId(),msg.getChunkNo()) == null) {//nobody has sent a chunk at the moment
                        Message response=null;
                        if (withEnhancement) {//Enhancement 2 - Chunks without body
                            try {
                                response = new Message(MessageType.CHUNK, version, Utils.peerID, msg.getFileId(), msg.getChunkNo());
                                Socket socket = new Socket(senderAddress,Utils.mdr.getPort());


                                OutputStream out = socket.getOutputStream();
                                DataOutputStream dos = new DataOutputStream(out);

                                byte[] chunk = loadChunk(chunkId);
                                dos.writeInt(chunk.length);
                                if (chunk.length > 0) {
                                    dos.write(chunk, 0, chunk.length);
                                }

                                System.out.println("Sending chunk over TCP fileId(" + msg.getFileId() +") , chunkNo(" +  msg.getChunkNo() + ")");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }


                        }else
                            response = new Message(MessageType.CHUNK,version,Utils.peerID,msg.getFileId(),msg.getChunkNo(),loadChunk(chunkId));


                        response.send(Utils.mdr);



                    }
                }

                break;
            case CHUNK:
                //it's all handled by the protocol
                break;
            case DELETE:
                String fileId = msg.getFileId();
                if(hasFileChunks(fileId)) {
                    deleteFileChunks(fileId);
                    //update metadata
                    Utils.metadata.removeFileChunks(fileId);
                }

                break;
            case REMOVED:

                if(hasChunk(chunkId)) {
                    Utils.metadata.updateReplicationDegree(chunkId,msg.getSenderId(),false);



                    if(Utils.metadata.getPerceivedDegree(chunkId) < Utils.metadata.getDesiredDegree(chunkId)) { //initiate putchunk
                        Observer obs = new Observer(Utils.mdb);
                        Utils.sleepRandomTime(400);
                        obs.stop();


                        if(obs.getMessage(MessageType.PUTCHUNK,msg.getFileId(),msg.getChunkNo()) == null){//nobody has initiated putchunk protocol
                            //Protocol.putChunkProtocol(new Chunk(chunkId.getFileID(),chunkId.getChunkID(),FileManager.loadChunk(chunkId)),Utils.metadata.getDesiredDegree(chunkId));
                            PutChunk pc = new PutChunk(new Chunk(chunkId.getFileID(),chunkId.getChunkID(),FileManager.loadChunk(chunkId)),Utils.metadata.getDesiredDegree(chunkId));
                            Thread threadPc = new Thread(pc);
                            threadPc.start();
                        }

                    }
                }

                break;
            case STORED:
                if(hasChunk(chunkId)) {
                    Utils.metadata.updateReplicationDegree(chunkId,msg.getSenderId(),true);//update metadata


                    //FAILED try of enhancement 1
                   /* if(Utils.metadata.getPerceivedDegree(chunkId) >= Utils.metadata.getDesiredDegree(chunkId)) {//remove the chunk
                        //remove chunk from metadata
                        Utils.metadata.removeChunk(chunkId);
                        deleteChunk(chunkId);

                        System.out.println("Removing chunk-> " + chunkId.getFileID() + "   chunkNo-> " + chunkId.getChunkID());
                        //send removed msg
                        Message removed = new Message(MessageType.REMOVED, Utils.version, Utils.peerID, chunkId.getFileID(),chunkId.getChunkID());
                        removed.send(Utils.mc);

                    }*/


                }

                break;
        }
    }



}
