
/* POJO class */

public class Node {

    private int id;
    private String addr;
    private boolean master = false;

    public Node() {
    }

    public Node(int id, String addr, boolean master) {
        this.id = id;
        this.addr = addr;
        this.master = master;
    }

    public int getID() {
        return id;
    }

    public String getAddress() {
        return addr;
    }

    public boolean isMaster() {
        return master;
    }

    public void setID(int id) {
        this.id = id;
    }

    public void setAddress(String addr) {
        this.addr = addr;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }
}
