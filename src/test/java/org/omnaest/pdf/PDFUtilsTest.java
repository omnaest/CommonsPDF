/*******************************************************************************
 * Copyright 2021 Danny Kunz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
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
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.pdf.PDFUtils.DisplayResolution;
import org.omnaest.pdf.PDFUtils.TextSize;
import org.omnaest.utils.FileUtils;
import org.omnaest.utils.MatcherUtils;
import org.omnaest.utils.StringUtils;
import org.omnaest.utils.markdown.MarkdownUtils;
import org.omnaest.utils.table.Table;

public class PDFUtilsTest
{

    @Test
    @Ignore
    public void testGetPDFInstance() throws Exception
    {
        PDFUtils.getPDFInstance()
                .createEmptyPDF()
                .addBlankPage()
                .addTitle("Titel")
                .addText("Dies ist ein Test")
                .addText("Weitere Zeile")
                .addFooter("Footer text")
                .addBlankPage()
                .addTitle("Titel2")
                .addText("Dies ist ein Test")
                .build()
                .writeTo(new File("C:/Temp/test.pdf"));
    }

    @Test
    @Ignore
    public void testAddPNG() throws Exception
    {
        byte[] pngSmiley = org.apache.commons.io.FileUtils.readFileToByteArray(new File("C:\\Temp\\stocks2\\asmiley.png"));
        byte[] pngStocks = org.apache.commons.io.FileUtils.readFileToByteArray(new File("C:\\Temp\\stocks2\\atest.png"));
        PDFUtils.getPDFInstance()
                .createEmptyPDF()
                .addBlankPage()
                .addTitle("Titel")
                .addText("Dies ist ein Test")
                .addPNG(pngSmiley, 50, 50)
                .addText("Weitere Zeile")
                .addFooter("Footer text")
                .addBlankPage(DisplayResolution._640x480)
                .addTitle("Titel2")
                .addText("Dies ist ein Test")
                .addPNGAsBackground(pngStocks)
                .build()
                .writeTo(new File("C:/Temp/stocks2/atest.pdf"));
    }

    @Test
    @Ignore
    public void testColumns() throws Exception
    {
        PDFUtils.getPDFInstance()
                .createEmptyPDF()
                .addBlankPage()
                .addTitle("Titel")
                .withColumns(5,
                             page -> page.addText("Dies ist ein Test", "Dies auch", "Und dies", "Und das", "Und so weiter", "Und weiter weiter", "Und weiter",
                                                  "Und weiter", "Und weiter")
                                         .addText(TextSize.VERY_SMALL, "Dies ist ein Test", "Dies auch", "Und dies", "Und das", "Und so weiter",
                                                  "Und weiter weiter", "Und weiter", "Und weiter", "Und weiter"))
                .addFooter("Footer text")
                .build()
                .writeTo(new File("C:/Temp/testColumns.pdf"));
    }

    @Test
    @Ignore
    public void testAddFurtherPDF() throws Exception
    {
        byte[] furtherPDF = PDFUtils.getPDFInstance()
                                    .createEmptyPDF()
                                    .addBlankPage()
                                    .addTitle("Further PDF title")
                                    .build()
                                    .getAsByteArray();

        PDFUtils.getPDFInstance()
                .createEmptyPDF()
                .addBlankPage()
                .addTitle("Titel of main pdf")
                .addPagesOfFurtherPDF(furtherPDF)
                .build()
                .writeTo(new File("C:/Temp/test_add_further.pdf"));
    }

    @Test
    @Ignore
    public void testLoadPDF() throws InvalidPasswordException, IOException
    {
        Stream<String> tokens = PDFUtils.getPDFInstance()
                                        .loadPDF(new File("C:\\Google Drive\\Body odor research\\Literature\\ODOR THRESHOLDS.pdf"))
                                        .build()
                                        .getAsTextLines();
        //        String text = tokens.collect(Collectors.joining("\n"));

        //1     Acetaldehyde        75-07-0             C2H4O   44.05   0.0015 – 1,000  pungent, fruity,    suffocating,    fresh, green    C = 25 TWA = 200 –
        //2     Acetic Acid         64-19-7             C2H4O2  60.05   0.0004 – 204    pungent,    vinegar STEL = 15   TWA = 10    TWA = 10 –
        //18    Benzaldehyde        100-52-7            C7H6O   106.12  0.0015 – 783    bitter almond, fruit,   vanilla – – TWA = 2 DSEN
        //28    Butane, all isomers 106-97-8, 75-28-5   C4H10   58.12   0.421 – 5,048 natural gas STEL = 1,000 – –
        String index = "[0-9]+";
        String name = "[a-zA-Z0-9\\-\\, ]+";
        String cas = "[0-9\\-\\, ]+";
        String formula = "[a-zA-Z0-9]+";
        String mass = "[0-9\\\\.]+";
        String threshold = "([0-9\\.\\,]+)[^0-9]+([0-9\\.\\,]+)";
        String description = "[a-z\\,\\s]+";
        Pattern pattern = Pattern.compile("^(" + index + ")\\s+(" + name + ")\\s+(" + cas + ")\\s+(" + formula + ")\\s+(" + mass + ")\\s+" + threshold + "\\s+("
                + description + ")");
        Stream<String> lines = StringUtils.routeByMatch(tokens, "[0-9]+")
                                          .map(token -> token.collect(Collectors.joining("\t")))
                                          .peek(System.out::println)
                                          .map(token -> MatcherUtils.matcher()
                                                                    .of(pattern)
                                                                    .findIn(token)
                                                                    .get()
                                                                    .flatMap(match -> match.getSubGroupsAsStream())
                                                                    .collect(Collectors.joining("\t")))
                                          .filter(line -> org.apache.commons.lang3.StringUtils.isNotBlank(line));

        FileUtils.toLineStreamConsumer(new File("C:\\Z\\databases\\odor thresholds\\odor_thresholds.tsv"))
                 .accept(Stream.concat(Stream.of(Arrays.asList("id", "name", "CAS", "formula", "mass", "threshold min", "threshold max", "description")
                                                       .stream()
                                                       .collect(Collectors.joining("\t"))),
                                       lines));

        //        System.out.println(text);
    }

    @Test
    @Ignore
    public void testLoadPDF2() throws InvalidPasswordException, IOException
    {
        Stream<String> tokens = PDFUtils.getPDFInstance()
                                        .loadPDF(new File("C:\\Z\\data\\PatientID_MB000002BJ.pdf"))
                                        .build()
                                        .getAsTextLines();

        //        String text = tokens.collect(Collectors.joining("\n"));

        //15 1-Undecene, 4-methyl- 0.57 0.00 0.57  
        String id = "([0-9]+)";
        String compound = "([a-zA-Z0-9\\-\\,\\.\\[\\]\\(\\)\\s]+)";
        String gradient = "([0-9\\.]+)";
        String air = "([0-9\\.]+)";
        String breath = "([0-9\\.]+)";

        Pattern pattern = Pattern.compile("^\\s*" + id + "\\s+" + compound + "\\s+" + gradient + "\\s+" + air + "\\s+" + breath + "\\s*$");
        Stream<String> lines = tokens.peek(System.out::println)
                                     .map(token -> MatcherUtils.matcher()
                                                               .of(pattern)
                                                               .matchAgainst(token)
                                                               .map(match -> match.getSubGroupsAsStream()
                                                                                  .collect(Collectors.joining("\t")))
                                                               .orElse(null))
                                     .filter(line -> org.apache.commons.lang3.StringUtils.isNotBlank(line))
                                     .map(token -> token)
                                     .peek(System.out::println);

        FileUtils.toLineStreamConsumer(new File("C:\\Z\\data\\PatientID_MB000002BJ.tsv"))
                 .accept(Stream.concat(Stream.of(Arrays.asList("id", "compound", "gradient", "air", "breath")
                                                       .stream()
                                                       .collect(Collectors.joining("\t"))),
                                       lines));

        //        System.out.println(text);
    }

    @Test
    public void testWithMarkdownInterpreter() throws Exception
    {
        PDFUtils.getPDFInstance()
                .createEmptyPDF()
                .withMarkdownInterpreter(interpreter -> interpreter.accept(MarkdownUtils.builder()
                                                                                        .addHeading("Heading")
                                                                                        .addText("Some text ...")
                                                                                        .addTable(Table.newInstance()
                                                                                                       .addColumnTitles("Column1", "Column2", "Column3")
                                                                                                       .addRow("1:1", "1:2", "1:3")
                                                                                                       .addRow("2:1", "2:2", "2:3"))
                                                                                        .build()))
                .build()
                .writeTo(new File("C:/Temp/markdown_test.pdf"));
    }

}
