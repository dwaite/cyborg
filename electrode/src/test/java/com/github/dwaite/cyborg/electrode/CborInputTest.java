package com.github.dwaite.cyborg.electrode;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

import org.junit.Test;

import com.github.dwaite.cyborg.electrode.impl.CborDataInput;

public class CborInputTest {

	@Test
	public void test() throws Exception {
		System.out.println(CborInputTest.class.getResource("."));
		InputStream stream = CborInputTest.class.getResourceAsStream("/jscn_interop_test/test1.cbor");
		/*
		 * 20([{"map": "value", "array": ["one", "two", "three", 42], "bool": true, "neg": -42, "simple": [false, null, ""], "ints": [0, 1, 23, 24, 255, 256, 65535, 65536, 4294967295, 4294967296, 281474976710656, -281474976710656]}, 0, [1, 1, -7, 8, 1, -9, 1, 2, 6, 2, 6, 2, 8, 2, 2, 1, 2, 1, -8, 5, 1, -7, 4, 1, -10, 1, 2, 6, 2, 5, 2, 2, 1, 2, 1, -8, 1, 2, 2, 2, 2, 2, 3, 2, 3, 2, 4, 2, 4, 2, 6, 2, 6, 2, 11, 2, 11, 2, 16, 2, 16, 1, 1, 0]])
		 */
		assertThat(stream, is(notNullValue()));
		CborParser input = new CborDataInput(new DataInputStream(stream));
		OutputStreamWriter writer = new OutputStreamWriter(System.out);
//		StringWriter writer = new StringWriter();
		
		DiagnosticOutput output = new DiagnosticOutput(writer);
		input.forEachRemaining(t -> {
			try {
				output.next(t);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
//		System.out.write(writer.toString().getBytes());
		output.close();
		writer.flush();
	}

	@Test
	public void test2() throws Exception {
		HexBinaryAdapter adapter = new HexBinaryAdapter();
		byte cbor[] = adapter.unmarshal("bf6346756ef563416d7421ff");
		OutputStreamWriter writer = new OutputStreamWriter(System.out);
		CborParser input = new CborDataInput(new DataInputStream(new ByteArrayInputStream(cbor)));

		DiagnosticOutput output = new DiagnosticOutput(writer);
		input.forEachRemaining(t -> {
			try {
				output.next(t);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		output.close();
		writer.flush();
		
	}
}
