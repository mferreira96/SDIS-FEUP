package network;



import logic.Message;
import logic.MessageType;

import java.util.ArrayList;
import java.util.Collections;

public class Observer {

    private ArrayList<Message> receivedMessages;
    private MulticastChannelWrapper channelToObserve;

    public Observer(MulticastChannelWrapper channelToObserve) {
        this.channelToObserve = channelToObserve;
        receivedMessages=new ArrayList<>();
        channelToObserve.addObserver(this);
    }

    public void update(Message msg){
        receivedMessages.add(msg);
    }

    public int getMessageNumber(MessageType type , String FileId, int chunkNo){
        int res=0;

        for (Message m: receivedMessages) {
            if(m.getType() == type && FileId.equals(m.getFileId()) && chunkNo == m.getChunkNo())
                res++;
        }

        return res;
    }



    public boolean existsType(MessageType type){
        return (Collections.frequency(receivedMessages,type)>0);
    }


    public void stop(){
        channelToObserve.removeObserver(this);
    }


}