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

    public static void runQueries() {
        String solrUrl = "http://localhost:8983/solr";
        HttpSolrClient client = new HttpSolrClient.Builder(solrUrl)
                .withConnectionTimeout(10000)
                .withSocketTimeout(60000)
                .build();

        try {
            int queryCounter = 1;
            Path outputFile = Paths.get("data/queryResults");
            if (Files.exists(outputFile)) {
                PrintWriter writer = new PrintWriter(outputFile.toString(), "UTF-8");
                writer.print("");
                writer.close();
            } else {
                Files.createFile(outputFile);
            }

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

                StringBuilder queryWords = new StringBuilder();
//                queryWordsWIthOr.append(T + word +
//                                        A + word +
//                                        B + word +
//                                        W + word);
                queryWords.append("text:" + word);
                while (tokenizer.hasMoreTokens()) {
                    word = tokenizer.nextToken();
//                    queryWordsWIthOr.append(" OR " + T + word +
//                                                A + word +
//                                                B + word +
//                                                W + word);
                    queryWords.append(" OR text:" + word);
                }

                StringBuilder reRankWords = constructRerankQuery(currentQuery);

                Map<String, String> queryParamMap = new HashMap<>();
                queryParamMap.put("q", queryWords.toString());
                //queryParamMap.put("rq", "{!rerank reRankQuery=$rqq reRankDocs=20 reRankWeight=3}");
                queryParamMap.put("rqq", reRankWords.toString());
                queryParamMap.put("fl", "identifier,score");
                queryParamMap.put("rows", "500");

                MapSolrParams queryParams = new MapSolrParams(queryParamMap);

                QueryResponse response = client.query("sr1", queryParams);

                SolrDocumentList documents = response.getResults();

                for (SolrDocument document : documents) {
                    String identifier = document.getFieldValue("identifier").toString()
                            .substring(1, document.getFieldValue("identifier").toString().indexOf("]"));
                    writer.println(queryCounter + " 0 " + identifier + " 1 " + document.getFieldValue("score") + " STANDARD");
                }
                queryCounter++;
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static StringBuilder constructRerankQuery(String query) {
        StringBuilder rerankQuery = new StringBuilder();
        rerankQuery.append("title:");

        ArrayList<String> words = new ArrayList<>();
        ArrayList<Integer> wordLenghts = new ArrayList<>();

        StringTokenizer tokenizer = new StringTokenizer(query, " ");
        words.add(tokenizer.nextToken());

        wordLenghts.add(words.get(words.size() - 1).length());
        while (tokenizer.hasMoreTokens()) {
            words.add(tokenizer.nextToken());
            wordLenghts.add(words.get(words.size() - 1).length());
        }

        ArrayList<Integer> sortedLengths = new ArrayList<>(wordLenghts);
        Collections.sort(sortedLengths);

        int counter = sortedLengths.size()-1;
        int max = sortedLengths.get(counter);

        boolean first = true;
        for(int i = 0; i < wordLenghts.size(); i++){
            if(max == wordLenghts.get(i)){
                if(first){
                    rerankQuery.append(words.get(i));
                    first = false;
                }
                else{
                    rerankQuery.append(" OR title:" +words.get(i));
                }
            }
        }
        return rerankQuery;
    }
}