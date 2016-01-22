
/* POJO class */

public class Node {

    private int ID;
    private String addr;
    private boolean Master = false;

    public Node() {
    }

    public Node(int ID, String addr, boolean Master) {
        this.ID = ID;
        this.addr = addr;
        this.Master = Master;
    }

    public int getID() {
        return ID;
    }

    public String getAddress() {
        return addr;
    }

    public boolean isMaster() {
        return Master;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public void setAddress(String addr) {
        this.addr = addr;
    }

    public void setMaster(boolean Master) {
        this.Master = Master;
    }
}
