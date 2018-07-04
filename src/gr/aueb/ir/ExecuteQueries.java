package gr.aueb.ir;

import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.MapSolrParams;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ExecuteQueries {
    // arraylist that each query is going to be stored at
    private static ArrayList<Query> queries;

    public static void main(String [ ] args) {
        // read the cran.qry and saves its transformed version to an arraylist of queries
        readAndTransformQueries("data/cran.qry");

        boolean useRerank = false;
        // exexutes the queries
        runQueries(useRerank);

        // modifies the creanqrel file to a form that is accepted to the trec eval
        ModifyCollection.modifyQrelFile("data/cranqrel");
    }

    public static void readAndTransformQueries(String fileName) {
        try {
            // we use buffereader to read the input file
            BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));

            queries = new ArrayList<>();
            Query queryToBeAdded = null;

            boolean withinQuery = false;
            StringBuilder currentQuery = null;

            // read the file line by line
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                currentLine = currentLine.trim();

                // if the line starts with .I
                if (currentLine.startsWith(".I")) {
                    // if we have already read another query
                    if (withinQuery) {
                        // we add the query string itslef of the previous query, that we just finished reading to the querytobeadded info
                        queryToBeAdded.setQuery(currentQuery.toString());
                        queries.add(queryToBeAdded);
                        withinQuery = false;
                    }
                    // and we create a new query object, and set its id
                    queryToBeAdded = new Query();
                    queryToBeAdded.setId(Integer.parseInt(currentLine.substring(2).trim()));
                // if the line starts with .W
                } else if (currentLine.startsWith(".W")) {
                    // we updated the flag's value and initialize the stringbuilder that we are going to store the query string itself att
                    withinQuery = true;
                    currentQuery = new StringBuilder();
                // if the line we read doenst start with a .symbol, it means it is a line with the query string itself so we simple append it to the string builder
                } else {
                    currentQuery.append(currentLine + " ");
                }
            }
            // we add the query string itslef to the last query
            queryToBeAdded.setQuery(currentQuery.toString());
            queries.add(queryToBeAdded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void runQueries(boolean reranked) {
        // we create our solr client
        String solrUrl = "http://localhost:8983/solr";
        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl)
                .withConnectionTimeout(10000)
                .withSocketTimeout(60000)
                .build();

        try {
            // we run our queries one by one and write the results to the queryResults.txt
            int queryCounter = 1;
            Path outputFile = Paths.get("data/queryResults");
            if (Files.exists(outputFile)) {
                PrintWriter writer = new PrintWriter(outputFile.toString(), "UTF-8");
                writer.print("");
                writer.close();
            } else {
                Files.createFile(outputFile);
            }

            // we simple replace some special chars with space
            PrintWriter writer = new PrintWriter(outputFile.toString(), "UTF-8");
            for (Query q : queries) {
                String currentQuery = q.getQuery().replace("(", " ")
                        .replace(")", " ")
                        .replace(".", " ")
                        .replace(";", " ")
                        .replace(":", " ")
                        .replace(",", " ")
                        .replace("-", " ");

                StringTokenizer tokenizer = new StringTokenizer(currentQuery, " ");
                String word = tokenizer.nextToken();

                // we create our query param to be input to solr
                StringBuilder queryWords = new StringBuilder();
                queryWords.append("text:" + word);
                while (tokenizer.hasMoreTokens()) {
                    word = tokenizer.nextToken();
                    queryWords.append(" OR text:" + word);
                }

                Map<String, String> queryParamMap = new HashMap<>();

                // contstructs a rerank query in case its needed
                if(reranked) {
                    String reRankWord = maxLengthWordInQuery(currentQuery);

                    queryParamMap.put("q", queryWords.toString());
                    queryParamMap.put("rq", "{!ltr model=myEfiModel reRankDocs=20 efi.word1=" + reRankWord.trim() +
                            " efi.word2=" + reRankWord.trim() + "}");
                    queryParamMap.put("fl", "identifier,score");
                    queryParamMap.put("rows", "500");
                }
                // else we create a simple query
                else{
                    queryParamMap.put("q", queryWords.toString());
                    queryParamMap.put("fl", "identifier,score");
                    queryParamMap.put("rows", "500");
                }

                MapSolrParams queryParams = new MapSolrParams(queryParamMap);

                QueryResponse response = client.query("sr1", queryParams);

                SolrDocumentList documents = response.getResults();

                // we write the retrieved docs to the reults file using a format of "qureyId, iter docId rank score Standar" as needed by treceval, in order to use treceval later for evaluation
                for (SolrDocument document : documents) {
                    String identifier = document.getFieldValue("identifier").toString()
                            .substring(1, document.getFieldValue("identifier").toString().indexOf("]"));
                    writer.println(queryCounter + " 0 " + identifier + " 1 " + document.getFieldValue("score") + " STANDARD");
                }
                // we use query counter to set the queries ids due to error to the cran.qry file
                queryCounter++;
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // returns the word having the longest lenght withing the query passed
    private static String maxLengthWordInQuery(String query) {
        String rerankQuery = "";

        // arraylist with elements the words of the query
        ArrayList<String> words = new ArrayList<>();
        // arraylist with elements the length of the words of the query. "paraller" to the above arraylist
        ArrayList<Integer> wordLenghts = new ArrayList<>();

        // we populate both arraylists
        StringTokenizer tokenizer = new StringTokenizer(query, " ");
        words.add(tokenizer.nextToken());

        wordLenghts.add(words.get(words.size() - 1).length());
        while (tokenizer.hasMoreTokens()) {
            words.add(tokenizer.nextToken());
            wordLenghts.add(words.get(words.size() - 1).length());
        }

        // we create a copy of the arraylist containing the lengths and sort it
        ArrayList<Integer> sortedLengths = new ArrayList<>(wordLenghts);
        Collections.sort(sortedLengths);

        // we get the max lenght of the words
        int counter = sortedLengths.size()-1;
        int max = sortedLengths.get(counter);

        // we return the worlds with that max lenght
        for(int i = 0; i < wordLenghts.size(); i++){
            if(max == wordLenghts.get(i)){
                rerankQuery = words.get(i);
                break;
            }
        }
        return rerankQuery;
    }
}