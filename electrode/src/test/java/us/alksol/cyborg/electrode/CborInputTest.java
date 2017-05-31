package us.alksol.cyborg.electrode;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.junit.Test;

import us.alksol.cyborg.electrode.impl.CborDataInput;

public class CborInputTest {

	@Test
	public void test() throws IOException {
		System.out.println(CborInputTest.class.getResource("."));
		InputStream stream = CborInputTest.class.getResourceAsStream("/jscn_interop_test/test1.cbor");
		assertThat(stream, is(notNullValue()));
		CborParser input = new CborDataInput(new DataInputStream(stream));
		Writer writer = new OutputStreamWriter(System.out);
		DiagnosticOutput output = new DiagnosticOutput(input, writer);
		output.process();
		writer.flush();
		writer.close();
	}

}
