package gr.aueb.ir;

public class main {
    public static void main(String [ ] args) {
        // read the cran.qry and saves its transformed version to an arraylist of queries
        ExecuteQueries.readAndTransformQueries("data/cran.qry");
        // exexutes the queries
        ExecuteQueries.runQueries();

        // modifies the creanqrel file to a form that is accepted to the trec eval
        ModifyCollection.modifyQrelFile("data/cranqrel");
    }
}
