/*

	Copyright 2017 Danny Kunz

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.


*/
package org.omnaest.pdf;

import java.io.File;

import org.junit.Test;

public class PDFUtilsTest
{

	@Test
	public void testGetPDFInstance() throws Exception
	{
		PDFUtils.getPDFInstance()
				.createEmptyPDF()
				.addBlankPage()
				.addTitle("Titel")
				.addText("Dies ist ein Test")
				.addText("Weitere Zeile")
				.addBlankPage()
				.addTitle("Titel2")
				.addText("Dies ist ein Test")
				.build()
				.write(new File("C:/Temp/test.pdf"));
	}

}
