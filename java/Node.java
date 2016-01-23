
/* POJO class */

public class Node {

    private int id;
    private String addr;
    private boolean Master = false;

    public Node() {
    }

    public Node(int id, String addr, boolean Master) {
        this.id = id;
        this.addr = addr;
        this.Master = Master;
    }

    public int getID() {
        return id;
    }

    public String getAddress() {
        return addr;
    }

    public boolean isMaster() {
        return Master;
    }

    public void setID(int id) {
        this.id = id;
    }

    public void setAddress(String addr) {
        this.addr = addr;
    }

    public void setMaster(boolean Master) {
        this.Master = Master;
    }
}
