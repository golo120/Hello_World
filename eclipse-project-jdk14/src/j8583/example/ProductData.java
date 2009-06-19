package j8583.example;

/** This class is merely used to illustrate the use of CustomField encoder/decoders.
 * 
 * @author Enrique Zamudio
 */
public class ProductData {

	private String pid;
	private int catid;

	public ProductData(){}

	public ProductData(int category, String product) {
		catid = category;
		pid = product;
	}

	public void setProductId(String value) {
		pid = value;
	}
	public String getProductId() {
		return pid;
	}

	public void setCategoryId(int value) {
		catid = value;
	}
	public int getCategoryId() {
		return catid;
	}

}
