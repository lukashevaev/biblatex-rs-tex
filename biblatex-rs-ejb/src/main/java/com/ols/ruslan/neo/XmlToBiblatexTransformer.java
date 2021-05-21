package com.ols.ruslan.neo;


import com.ols.ruslan.neo.exceptions.BiberDisabledException;
import com.ols.ruslan.neo.exceptions.LatexDisabledException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.ejb.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;


@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@TransactionManagement(TransactionManagementType.CONTAINER)
@Singleton(name = "XmlToBiblatexTransformer")
@Startup
@Remote(MediaTypeTransformerFacade.class)
@EJB(name = "java:global/ruslan/mediaType/application/xml/application/biblatex", beanInterface = MediaTypeTransformerFacade.class)
public class XmlToBiblatexTransformer implements MediaTypeTransformerFacade {
    private static final Logger log = Logger.getLogger(XmlToBiblatexTransformer.class
            .getName());
    private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private static Templates templates;


    @PostConstruct
    public void startup() {
        log.info("Startup");
        try {
            templates = transformerFactory.newTemplates(new StreamSource(
                    XmlToBiblatexTransformer.class.getClassLoader().getResourceAsStream(
                            "RUSMARC2BibTex.xsl")));

        } catch (TransformerConfigurationException e) {
            log.severe("Unable to initialise templates: " + e.getMessage());
            e.printStackTrace();
        }
    }



    @Override
    public byte[] transform(byte[] content, String encoding) {
        // Создаем трансформер для преобразования одного xml в другой
        try {
            Transformer transformer = templates.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            DOMResult result = new DOMResult();

            // Создаем источник для преобразования из поступившего массива байт
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(content));

            //Трансформация,парсинг и создание нового формата
            transformer.transform(new DOMSource(document), result);
            Map<String, String> fields = XmlParser.parse((Document) result.getNode());
            BibTexBuilder bibTexBuilder = new BibTexBuilder(fields);

            String biblatex = getNewFormat("apa", bibTexBuilder.buildBibtex());
            if (biblatex == null || biblatex.isEmpty() || biblatex.length() < 15) throw new NullPointerException();
            return getNewFormat("apa", bibTexBuilder.buildBibtex()).getBytes(encoding);
        } catch (ParserConfigurationException | IOException | TransformerException | SAXException | LatexDisabledException | BiberDisabledException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public String transform(byte[] content, String fileName, String encoding) {
        // Создаем трансформер для преобразования одного xml в другой
        try {
            Transformer transformer = templates.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            DOMResult result = new DOMResult();

            // Создаем источник для преобразования из поступившего массива байт
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(content));

            //Трансформация,парсинг и создание нового формата
            transformer.transform(new DOMSource(document), result);
            Map<String, String> fields = XmlParser.parse((Document) result.getNode());
            BibTexBuilder bibTexBuilder = new BibTexBuilder(fields);

            String biblatex = getNewFormat(fileName, bibTexBuilder.buildBibtex());
            if (biblatex == null || biblatex.isEmpty() || biblatex.length() < 15) throw new NullPointerException();
            return getNewFormat("apa", bibTexBuilder.buildBibtex());
        } catch (ParserConfigurationException | IOException | TransformerException | SAXException | LatexDisabledException | BiberDisabledException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    // запуск команд через командную строку (из диплома по латеху прошлого года)
    private String executeCmdCommand(String command) {
        StringBuilder messageBuilder = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            if (isWindows) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                messageBuilder.append(line).append("\n");
            }
            process.waitFor();
            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return messageBuilder.toString();
    }

    // Метод для получения какого-то нового формата
    private String getNewFormat(String fileName, String bibtex) throws IOException {

        String latexMessage;
        String biberMessage = null;
        fillBibtexFile(bibtex);

        // получение тех-файла (заранее прописаны, разработчики добавляют новые)
        File file = new File(String.format("biblatex-rs-ejb\\src\\main\\resources\\%s.tex", fileName));
        // в командной строке нужно прописывать полный путь
        String fullPath = file.getAbsoluteFile().getAbsolutePath();
        // название результирующего файла
        String resultFile1 = new File(String.format("%s.pdf", fileName)).getAbsolutePath();
        // команды для запуска преобразования латех+бибер
        String pdfLatexCommand = "pdflatex  " + fullPath;
        String biberCommand = "biber " + resultFile1.replaceAll("\\.pdf", "");
        latexMessage = executeCmdCommand(pdfLatexCommand);
        // для корректной работы бибера должен создаться bcf-файл
        Path bibliographyFile = Paths.get(file.getAbsoluteFile().getAbsolutePath().replaceAll("\\.tex", ".bcf"));
        if (Files.exists(bibliographyFile)) {
            biberMessage = executeCmdCommand(biberCommand);
        }

        if (latexMessage.equals("")) {
            throw new LatexDisabledException("Latex message is empty");
        } /*else if (biberMessage == null || "".equals(biberMessage)) {
            throw new BiberDisabledException("Biber message is empty");
        }*/

        String resultFile = null;
        Path path = Paths.get(String.format("%s.pdf", fileName));

        if (Files.exists(path)) {
            resultFile = path.toFile().getAbsoluteFile().getAbsolutePath();
        }

        String result = convertPDFToText(resultFile1).orElse("NotFound");
        return result.split("литературы").length > 1 ? result.split("литературы")[1].trim() : result;

    }

    private Optional<String> convertPDFToText(String filePath) {
        if (filePath == null) return Optional.empty();
        try {
            PDDocument pddDoc = PDDocument.load(filePath);
            PDFTextStripper reader = new PDFTextStripper();
            String pageText = reader.getText(pddDoc);
            pddDoc.close();
            //if (pageText.length() < 50) return null;
            return Optional.ofNullable(pageText);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return Optional.empty();
    }

    //заполнение биб файла данными полуенными полсле преобразования в бибтех
    private void fillBibtexFile(String bibtex) {
        //InputStream stream = XmlToBiblatexTransformer.class.getClassLoader().getResourceAsStream("bibtex.bib");
        File file = new File("bibtex.bib");
        try (FileWriter writer = new FileWriter(file)) {
            boolean created = file.createNewFile();
            if (created) {
                System.out.println("Bibtex file was created");
            } else {
                System.out.println("Bibtex file already exists");
            }
            writer.write(bibtex);
            writer.flush();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
