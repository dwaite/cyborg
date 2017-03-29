package us.alksol.cyborg.implant;

import us.alksol.cyborg.implant.DataType.Major;

public class CborIncorrectMajorTypeException extends CborException {
	private static final long serialVersionUID = 1L;

	private final Major expected;
	private final DataType header;

	public CborIncorrectMajorTypeException(DataType header, Major expected) {
		super("Received header " + header + ", expected major type " + expected);
		this.header = header;
		this.expected = expected;
	}

	public Major getExpected() {
		return expected;
	}

	public Major getActual() {
		return header.getMajorType();
	}
	
	public DataType getHeader() {
		return header;
	}

}
