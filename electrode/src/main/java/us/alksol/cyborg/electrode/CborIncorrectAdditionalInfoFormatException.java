package us.alksol.cyborg.electrode;

import java.util.Arrays;

public class CborIncorrectAdditionalInfoFormatException extends CborException {
	private static final long serialVersionUID = 1L;

	private final AdditionalInfoFormat[] expected;
	private final InitialByte header;

	public CborIncorrectAdditionalInfoFormatException(InitialByte header, AdditionalInfoFormat expected) {
		super("Received header " + header + ", expected additional info format " + expected);
		this.header = header;
		this.expected = new AdditionalInfoFormat[] {expected};
	}

	public CborIncorrectAdditionalInfoFormatException(InitialByte header, AdditionalInfoFormat... expected) {
		super("Received header " + header + ", expected one of additional info formats " + Arrays.toString(expected));
		this.header = header;
		this.expected = Arrays.copyOf(expected, expected.length);
	}

	public AdditionalInfoFormat[] getExpected() {
		return Arrays.copyOf(expected, expected.length);
	}

	public AdditionalInfoFormat getActual() {
		return header.getAdditionalInfoFormat();
	}
	
	public InitialByte getHeader() {
		return header;
	}

}
