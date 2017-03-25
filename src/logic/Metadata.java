package logic;



import java.io.Serializable;
import java.util.HashMap;

public class Metadata implements Serializable{
    private HashMap<String, Integer[]> chunksMetadata;
    private int maximumDiskSpace;
    //indexes
    private static int CURRENT_REPLICATION_DEGREE=0;
    private static int DESIRED_REPLICATION_DEGREE=1;


    public Metadata() {
        chunksMetadata = new HashMap<>();
        maximumDiskSpace = 64000;
    }

    public HashMap<String, Integer[]> getChunksMetadata() {
        return chunksMetadata;
    }

    public int getMaximumDiskSpace() {
        return maximumDiskSpace;
    }

    public void setMaximumDiskSpace(int maximumDiskSpace) {
        this.maximumDiskSpace = maximumDiskSpace;
    }

    public void addChunk(String chunkId,int desiredRepDeg){
        Integer[] degrees = new Integer[2];
        degrees[CURRENT_REPLICATION_DEGREE]=0;
        degrees[DESIRED_REPLICATION_DEGREE]=desiredRepDeg;

        chunksMetadata.put(chunkId,degrees);
    }

    public void updateReplicationDegree(String chunkId,int val) {
        Integer[] currDegree = chunksMetadata.get(chunkId);
        currDegree[CURRENT_REPLICATION_DEGREE] += val;
        chunksMetadata.put(chunkId, currDegree);
    }


    @Override
    public String toString() {
        String res="Metadata\n";

        for(HashMap.Entry<String, Integer[]> entry : chunksMetadata.entrySet()) {
            String key = entry.getKey();
            Integer[] val = entry.getValue();

            res+= "key-> " + key + " Current_Rep_Deg-> " + val[0] + " Desired_Rep_Deg-> " + val[1] + "\n";
        }

            res+= "\n  maximumDiskSpace=" + maximumDiskSpace;
        return res;
    }
}