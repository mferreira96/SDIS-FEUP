package common;



import java.rmi.Remote;
import java.rmi.RemoteException;


public interface ServerInterface extends Remote{
     void makeRequest(Request req,CallBackInterface callBack)throws RemoteException;
}
