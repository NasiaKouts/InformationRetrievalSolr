package gr.aueb.ir;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;

public class ModifyCollection {

    // Calls parseFile method that is responsible to transform the cran.all.1400 file, that contains the collection's docs to xml file
    public static void main(String [ ] args) {
        ModifyCollection.parseFile("data/cran.all.1400");
    }

    /*
     * Transfroms the cran.all.1400 file to a xml file, suitable to work with solr
     */
    public static boolean parseFile(String fileName){
        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document xmlDoc = documentBuilder.newDocument();

            // create the root element "add"
            Element rootElement = xmlDoc.createElement("add");

            // We use bufferReader to read the input file
            BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));

            // flag used to act respectively if the document we are reading is the first document read or not
            boolean notFirstTravel = false;

            /* stringbuilders and boolean flags for each of the followings:
             * title, author, bAttr, text, that are the names we give to the .T, .A, .B, .W sections of the input file.
             * Stringbuilders are used to store the strings following the corresponding .symbol, till the next .symplo is found.
             * flags, are used in order to know when we read an non "tag" line, meaning a line without .symbol, in which stringBuilder we have to save the content of the line.
             */
            StringBuilder currentTitle = null;
            boolean withinTitle = false;
            StringBuilder currentAuthor = null;
            boolean withinAuthor = false;
            StringBuilder currentBAttr = null;
            boolean withinBibliography = false;
            StringBuilder currentText = null;
            boolean withinText = false;

            /* flag array used to know if we already met the same .symbol for the specific document we are reading,
             * in order to ignore the info - lines following.
             * flagRest, is a flag used as we use the flugs above, like withinTitle, in order to know that we have to ignore the lines following.
             */
            boolean[] alreadyFound = new boolean[4];
            boolean flagRest = false;

            Element document = null;

            // we read line by line the input file, till there is no line left
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                currentLine = currentLine.trim();
                // we check if the line starts with .symbol - is a tag line
                String startsWith = currentLine.substring(0, 2);
                switch (startsWith){
                    // if it is a .I line
                    case ".I":{
                        // if it is not the first document in the file
                        if(notFirstTravel){
                            // -------------------------------------------
                            // we add all our doc's fields as its children, and set the corresponding name attribute, and add as a child a text node with the corresponding value
                            if(currentTitle != null){
                                if(currentTitle.length() == 0) currentTitle.append(" ");
                                Element title = xmlDoc.createElement("field");
                                title.setAttribute("name", "title");
                                title.appendChild(xmlDoc.createTextNode(currentTitle.toString()));
                                document.appendChild(title);
                                currentTitle = null;
                            }

                            if(currentAuthor != null) {
                                if(currentAuthor.length() == 0) currentAuthor.append(" ");
                                Element author = xmlDoc.createElement("field");
                                author.setAttribute("name", "author");
                                author.appendChild(xmlDoc.createTextNode(currentAuthor.toString()));
                                document.appendChild(author);
                                currentAuthor = null;
                            }

                            if(currentBAttr != null){
                                if(currentBAttr.length() == 0) currentBAttr.append(" ");
                                Element bAttr = xmlDoc.createElement("field");
                                bAttr.setAttribute("name", "bAttr");
                                bAttr.appendChild(xmlDoc.createTextNode(currentBAttr.toString()));
                                document.appendChild(bAttr);
                                currentBAttr = null;
                            }

                            if(currentText != null){
                                if(currentText.length() == 0) currentText.append(" ");
                                Element text = xmlDoc.createElement("field");
                                text.setAttribute("name", "text");
                                text.appendChild(xmlDoc.createTextNode(currentText.toString()));
                                document.appendChild(text);
                                currentText = null;

                                // append the completed document as a child to the root element
                                rootElement.appendChild(document);
                            }
                            // -------------------------------------------
                        }

                        // we update the values of the various flags
                        notFirstTravel = true;

                        withinTitle = false;
                        withinAuthor = false;
                        withinBibliography = false;
                        withinText = false;

                        for(int i = 0; i < 4; i++){
                            alreadyFound[i] = false;
                        }

                        // we create new "doc" element" and
                        document = xmlDoc.createElement("doc");

                        // we get the id of the document we are about to read
                        String idValue = currentLine.substring(3).trim();

                        // we create a new field element with a name attribute "identifier"
                        // we add to it as a child a textNode that contains the id value of the doc
                        // we add it as a child to the doc element
                        Element id = xmlDoc.createElement("field");
                        id.setAttribute("name", "identifier");
                        id.appendChild(xmlDoc.createTextNode(idValue));
                        document.appendChild(id);

                        break;
                    }
                    // if it is a .T line
                    case ".T":{
                        // we initialize the corresponding string builder and set the rest string builders to null
                        currentTitle = new StringBuilder();
                        currentAuthor = null;
                        currentBAttr = null;
                        currentText = null;

                        // we update the values of various flags
                        withinTitle = true;
                        withinAuthor = false;
                        withinBibliography = false;
                        withinText = false;

                        alreadyFound[0] = true;
                        flagRest = false;

                        break;
                    }
                    // if it is a .A line
                    case ".A":{
                        // there are errors in format of the input file and there is a doc with multiple .A tags that are not indeed authors
                        // in case we find a duplicate .A, then we update the flageRest value
                        if(alreadyFound[1]){
                            flagRest = true;
                        }
                        // otherwise
                        else{
                            // we initialize the corresponding string builder and set update the values of some flags
                            currentAuthor = new StringBuilder();
                            alreadyFound[1] = true;
                            flagRest = false;
                        }

                        // we update the values of various flags
                        withinTitle = false;
                        withinAuthor = true;
                        withinBibliography = false;
                        withinText = false;

                        break;
                    }
                    // if it is a .B line
                    case ".B":{
                        // there are errors in format of the input file and there is a doc with multiple .A tags that are not indeed authors
                        // in case we find a duplicate .B, then we update the flageRest value
                        if(alreadyFound[2]){
                            flagRest = true;
                        }
                        // otherwise
                        else{
                            // we initialize the corresponding string builder and set update the values of some flags
                            currentBAttr = new StringBuilder();
                            alreadyFound[2] = true;
                            flagRest = false;
                        }

                        // we update the values of various flags
                        withinTitle = false;
                        withinAuthor = false;
                        withinBibliography = true;
                        withinText = false;

                        break;
                    }
                    // if it is a .W line
                    case ".W":{
                        // there are errors in format of the input file and there is a doc with multiple .A tags that are not indeed authors
                        // in case we find a duplicate .B, then we update the flageRest value
                        if(alreadyFound[3]){
                            flagRest = true;
                        }
                        // otherwise
                        else{
                            // we initialize the corresponding string builder and set update the values of some flags
                            currentText = new StringBuilder();
                            alreadyFound[3] = true;
                            flagRest = false;
                        }

                        // we update the values of various flags
                        withinTitle = false;
                        withinAuthor = false;
                        withinBibliography = false;
                        withinText = true;

                        break;
                    }
                    // if the line we read doesnt start with .symbol we append the content of the line to the corresponding stringbuilder
                    default:{
                        // if we are in a duplicate
                        if(flagRest){
                            // ignore duplucate .Symbol section at the same doc
                        }
                        else if(withinTitle){
                            currentTitle.append(currentLine + " ");
                        }
                        else if(withinAuthor){
                            currentAuthor.append(currentLine + " ");
                        }
                        else if(withinBibliography){
                            currentBAttr.append(currentLine + " ");
                        }
                        else if(withinText){
                            currentText.append(currentLine + " ");
                        }

                    }
                }
            }
            // -------------------------------------------
            // because the last doc's info havent been added, we add all our doc's fields as its children, and set the corresponding name attribute, and add as a child a text node with the corresponding value
            if(currentTitle != null){
                if(currentTitle.length() == 0) currentTitle.append(" ");
                Element title = xmlDoc.createElement("field");
                title.setAttribute("name", "title");
                title.appendChild(xmlDoc.createTextNode(currentTitle.toString()));
                document.appendChild(title);
            }

            if(currentAuthor != null) {
                if(currentAuthor.length() == 0) currentAuthor.append(" ");
                Element author = xmlDoc.createElement("field");
                author.setAttribute("name", "author");
                author.appendChild(xmlDoc.createTextNode(currentAuthor.toString()));
                document.appendChild(author);
            }

            if(currentBAttr != null){
                if(currentBAttr.length() == 0) currentBAttr.append(" ");
                Element bAttr = xmlDoc.createElement("field");
                bAttr.setAttribute("name", "bAttr");
                bAttr.appendChild(xmlDoc.createTextNode(currentBAttr.toString()));
                document.appendChild(bAttr);
            }

            if(currentText != null){
                if(currentText.length() == 0) currentText.append(" ");
                Element text = xmlDoc.createElement("field");
                text.setAttribute("name", "text");
                text.appendChild(xmlDoc.createTextNode(currentText.toString()));
                document.appendChild(text);
            }
            // -------------------------------------------

            // we append the doc to the root element
            rootElement.appendChild(document);

            // we add the root element to the xmlDoc and we write it to the actual xml file, called newDocs
            xmlDoc.appendChild(rootElement);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(xmlDoc);
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            StreamResult result =  new StreamResult(new File("data/newDocs.xml"));
            transformer.transform(source, result);
            return true;
        }
        catch(Exception e){
            e.printStackTrace();
            return false;
        }
    }

    // modifies the cranqrel file, in order to be able to be used by trec eval, we simply add a new iter column filled with zeros
    public static void modifyQrelFile(String fileName){
        // arraylist that has as content the lines of the input file
        ArrayList<String> lines = new ArrayList<String>();
        String line = null;
        try{
            // we read the whole input file line by line and each line to the arraylist as a new element
            File f1 = new File(fileName);
            FileReader fr = new FileReader(f1);
            BufferedReader br = new BufferedReader(fr);
            line = br.readLine();
            boolean flag = true;
            while (line != null){
                // check if there is a new column needed to the input file, or if it is already been modified to the correct form before so we do not have to do anything
                if(!newColNeeded(line) && flag) {
                    return;
                }
                else{
                    flag = false;
                }
                // we replace the first space we meet with "space 0 space"
                line = line.replaceFirst(" ", " 0 ");
                lines.add(line);
                line = br.readLine();
            }
            fr.close();
            br.close();

            // we write the lines stored at the arraylist to the file erasing the previous content
            FileWriter fw = new FileWriter(f1);
            BufferedWriter out = new BufferedWriter(fw);
            for(String s : lines)
                out.write(s + "\n");
            out.flush();
            out.close();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    // checks if there is a new collumn needed
    private static boolean newColNeeded(String line){
        // we check if there is a columln needed by counting the number of spaces appeared
        String s = line.trim();
        int counter = 0;
        for( int i=0; i<s.length(); i++ ) {
            if( s.charAt(i) == ' ' ) {
                counter++;
            }
        }
        if(counter == 2) return true;
        return false;
    }

}
