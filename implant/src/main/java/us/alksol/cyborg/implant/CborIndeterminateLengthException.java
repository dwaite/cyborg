package us.alksol.cyborg.implant;

public class CborIndeterminateLengthException extends CborException {
	private static final long serialVersionUID = 1L;
	
	private final DataType dataType;

	public CborIndeterminateLengthException(DataType dataType) {
		super("Indeterminate length for data type " + dataType);
		this.dataType = dataType;
	}


	public DataType getDataType() {
		return dataType;
	}
}
