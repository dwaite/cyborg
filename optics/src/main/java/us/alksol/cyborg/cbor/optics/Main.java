package us.alksol.cyborg.cbor.optics;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import us.alksol.cyborg.implant.CborDataInput;
import us.alksol.cyborg.implant.CborException;
import us.alksol.cyborg.implant.CborInput;

public class Main {
	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	public static void main(String[] args) throws IOException, CborException {
		
		byte byteInput[] = hexStringToByteArray("c074323031332d30332d32315432303a30343a30305a");
		
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(byteInput));
		CborInput cborInput = new CborDataInput(input);
		try (OutputStreamWriter writer = new OutputStreamWriter(System.out)) {
			Optics optics = new Optics(cborInput, writer);
			optics.process();
		}
	}

}
