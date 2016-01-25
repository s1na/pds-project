public class Lamport{
	private int ID;
	private int Counter;
	
	public Lamport(){
		
	}
	
	public Lamport(int ID, int Counter){
		this.ID = ID;
		this.Counter = Counter;
	}
	
	public int Time(){
		return this.Counter;
	}
	
	public int Increment(){
		int inc = this.Counter + 1;
		return inc;
	}
	
	public void set(int val){
		this.Counter = val;
	}
	
	public void recv(int val){
		int iMax = Math.max(this.Counter, val);
		set(iMax + 1);
	}
	public int compare(Lamport lc){
		if(this.Counter > lc.Counter){
			return 1;
		} else if( this.Counter < lc.Counter ){
			return -1;
		} else {
			if(this.ID > lc.ID) {
				return 1;
			} else if(this.ID < lc.ID) {
				return -1;
			} else {
				return 0;
			}
		}
	}
	
}