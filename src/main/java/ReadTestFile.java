        import java.io.BufferedReader;
        import java.io.File;
        import java.io.FileReader;
        import java.io.IOException;
        import java.io.RandomAccessFile;
        import java.net.URI;
        import java.net.URISyntaxException;
        import java.text.SimpleDateFormat;
        import java.time.Duration;
        import java.time.Instant;
        import java.util.ArrayList;
        import java.util.Date;
        import java.util.List;

        import org.apache.commons.codec.binary.Base64;

        import com.google.gson.JsonArray;
        import com.google.gson.JsonElement;
        import com.google.gson.JsonObject;
        import com.rallydev.rest.RallyRestApi;
        import com.rallydev.rest.request.CreateRequest;
        import com.rallydev.rest.request.QueryRequest;
        import com.rallydev.rest.request.UpdateRequest;
        import com.rallydev.rest.response.CreateResponse;
        import com.rallydev.rest.response.QueryResponse;
        import com.rallydev.rest.response.UpdateResponse;
        import com.rallydev.rest.util.Fetch;
        import com.rallydev.rest.util.QueryFilter;
        import com.rallydev.rest.util.Ref;

public class ReadTestFile {
    static RallyRestApi restApi = null;
    static List<String> testSetRef;
    static String resultString = "";
    static String tcId = "";
    static String projectName = "Marconi";
    static String testEnv = "";

    static String fromDate = "2021-02-17";
    static String toDate = "2021-03-02";

    public static void main(String[] args) throws Exception {
        System.out.println();

        testEnv = args[0];
        // Connect to the Rally
        String baseURL = "https://rally1.rallydev.com";
        String apiKey = "_PoEI5cLyRFazfvH3M3sanqq2bCw0IE0SkvLEXohJvUI";

        Instant setupStart = Instant.now();

        try {
            restApi = new RallyRestApi(new URI(baseURL), apiKey);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        restApi.setApplicationName("testSet Update");
        QueryResponse res = null;
        try {
            res = restApi.query(new QueryRequest("workspace"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String workspaceRef = Ref.getRelativeRef(res.getResults().get(0).getAsJsonObject().get("_ref").getAsString());
        String projectRef = Ref.getRelativeRef(getRef(workspaceRef, "Project", projectName).get("_ref").getAsString());
        JsonObject currentIterationObj = getCurrentIterationRef(workspaceRef, projectRef);
        // System.out.println(getCurrentIterationRef(workspaceRef, projectRef).get("Name"));
        List<String> allTestSet = getAllTestSet(projectRef, currentIterationObj);
        //  System.out.println(allTestSet);
        JsonObject tsRef = getRef(workspaceRef, projectRef, "TestSet", testEnv  + currentIterationObj.get("Name").getAsString());
        Instant setupFinish = Instant.now();
        long setupTimeElapsed = Duration.between(setupStart, setupFinish).getSeconds();
        System.out.println("Seconds elapsed: " + setupTimeElapsed);

        // attached all the test result text file to to test case result.
        File folder = new File(args[1]);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                Instant start = Instant.now();

                String resultFileName = folder + "/" + file.getName();

                BufferedReader br = new BufferedReader(new FileReader(resultFileName));

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Test Passed")) {
                        resultString = "Pass";
                    } else if (line.contains("Test Failed")) {
                        resultString = "Fail";
                    } else if (line.contains("Test Case ID:")) {
                        String[] tokens = line.split(":");
                        tcId = tokens[2].trim();
                    }
                }

                if (tcId.isEmpty()) {
                    throw new Exception("Did not find test case ID value in file " + resultFileName);
                }

                if (resultString.isEmpty()) {
                    throw new Exception("Did not find test result value in file " + resultFileName);
                }

                JsonObject tcRef = queryRequest(workspaceRef, resultFileName, projectRef, "TestCase", tcId);
                //System.out.println("::::" + tcRef.get("_ref").getAsString());
                // System.out.println("filename:" + resultFileName);
                if(tcRef != null) {
                    addTSToTC(tsRef, tcRef);
                    addTCResult(tcId, workspaceRef, Ref.getRelativeRef(tsRef.get("_ref").getAsString()),
                            Ref.getRelativeRef(tcRef.get("_ref").getAsString()), resultFileName);
                }
                Instant finish = Instant.now();
                long timeElapsed = Duration.between(start, finish).getSeconds();
                System.out.println("Seconds elapsed: " + timeElapsed);

            }
        }
    }

    public static JsonObject getRef(String workspaceRef, String projectRef, String type, String name) {
        JsonObject ref = null;
        QueryRequest request = new QueryRequest(type);
        request.setFetch(
                new Fetch("Name", "ObjectID", "StartDate", "EndDate", "Project", "RevisionHistory", "TestSet"));
        request.setWorkspace(workspaceRef);
        request.setProject(projectRef);
        QueryResponse response = null;
        try {
            response = restApi.query(request);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response.wasSuccessful()) {
            for (JsonElement result : response.getResults()) {
                String typeName = result.getAsJsonObject().get("Name").getAsString();
                if (typeName.contains(name) && type.equals("TestSet")) {
                    ref = result.getAsJsonObject();
                    break;
                }
                if (typeName.equals(name)) {
                    ref = result.getAsJsonObject();
                    break;
                }
            }
        }
        return ref;
    }

    public static JsonObject getRef(String workspaceRef, String type, String name) {
        JsonObject ref = null;
        QueryRequest request = new QueryRequest(type);
        request.setFetch(
                new Fetch("Name", "ObjectID", "StartDate", "EndDate", "Project", "RevisionHistory", "TestSet"));
        request.setWorkspace(workspaceRef);
        request.setLimit(2);

        QueryResponse response = null;
        try {
            response = restApi.query(request);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response.wasSuccessful()) {

            for (JsonElement result : response.getResults()) {
                String typeName = result.getAsJsonObject().get("Name").getAsString();
                if (typeName.contains(name) && type.equals("TestSet")) {
                    ref = result.getAsJsonObject();
                    break;
                }

                if (typeName.equals(name)) {
                    ref = result.getAsJsonObject();
                    break;
                }

            }
        }
        return ref;
    }

    public static JsonObject getCurrentIterationRef(String workspaceRef, String projectRef) {
        QueryRequest currentIterationRequest = new QueryRequest("Iteration");
        currentIterationRequest.setFetch(new Fetch("FormattedID", "Name", "StartDate", "EndDate"));

        // String pattern = "yyyy-MM-dd'T'HH:mmZ";
        String pattern = "yyyy-MM-dd";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        String date = simpleDateFormat.format(new Date());
                currentIterationRequest
                .setQueryFilter(new QueryFilter("StartDate", "<=", fromDate).and(new QueryFilter("EndDate", ">=", toDate)));

        currentIterationRequest.setWorkspace(workspaceRef);
        currentIterationRequest.setProject(projectRef);

        QueryResponse response = null;
        try {
            response = restApi.query(currentIterationRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonObject iterationRef = response.getResults().get(0).getAsJsonObject();// .get("_ref").getAsString();
        return iterationRef;
    }

    public static List<String> getAllTestSet(String projectRef, JsonObject currentIterationObj) {
        String initWord = testEnv.split(" ")[0]; // Dev or Stage.
        List<String> testSetList = new ArrayList<String>();
        testSetRef = new ArrayList<String>();
        QueryRequest testSetRequest = new QueryRequest("TestSet");
        testSetRequest.setFetch(new Fetch("FormattedID", "Name"));
        testSetRequest.setQueryFilter(
                new QueryFilter("Iteration.Name", " = ", currentIterationObj.get("Name").getAsString()));
        testSetRequest.setProject(projectRef);
        QueryResponse response = null;
        try {
            response = restApi.query(testSetRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (response.wasSuccessful()) {
            if (response.getTotalResultCount() == 0) {
                addTestSet(projectRef, Ref.getRelativeRef(currentIterationObj.get("_ref").getAsString()),
                        initWord + " - " + currentIterationObj.get("Name").getAsString());
                testSetList.add(initWord);
            }

            for (JsonElement result : response.getResults()) {
                String testSetName = result.getAsJsonObject().get("Name").getAsString();
                if (testSetName.equalsIgnoreCase(initWord + " - " + currentIterationObj.get("Name").getAsString())) {
                    testSetList.add(initWord);
                    testSetRef.add(Ref.getRelativeRef(result.getAsJsonObject().get("_ref").getAsString()));
                }
            }
            if (testSetList.isEmpty()) {
                addTestSet(projectRef, Ref.getRelativeRef(currentIterationObj.get("_ref").getAsString()),
                        initWord + " - " + currentIterationObj.get("Name").getAsString());
                testSetList.add(initWord);
            }
        }
        return testSetList;
    }

    // type: Iteration,TestCase, TestSet,
    public static JsonObject queryRequest(String workspaceRef, String filename, String projectRef, String type, String id) {
        QueryRequest request = new QueryRequest(type);
        request.setFetch(new Fetch("FormattedID", "Name", "TestSets"));
        request.setWorkspace(workspaceRef);
        request.setProject(projectRef);
        request.setQueryFilter(new QueryFilter("FormattedID", "=", id));
        QueryResponse response = null;
        try {
            response = restApi.query(request);
            if (response.getTotalResultCount() == 0) {
                System.out.println(">>> Cannot find tag: " + id +" From File Name:  " + filename );
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        JsonObject jsonObject = response.getResults().get(0).getAsJsonObject();
        return jsonObject;
    }

    public static void addTestSet(String projectRef, String iterationIdRef, String testSetName) {

        JsonObject newTS = new JsonObject();
        newTS.addProperty("Project", projectRef);
        newTS.addProperty("Name", testSetName);
        newTS.addProperty("Iteration", iterationIdRef);
        newTS.addProperty("fetch", true);
        CreateRequest createRequest = new CreateRequest("testset", newTS);
        try {
            CreateResponse createResponse = restApi.create(createRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // add test case to test set
    public static void addTSToTC(JsonObject tsJsonObject, JsonObject testCaseJsonObject) {

        String testCaseRef = testCaseJsonObject.get("_ref").getAsString();
        int numberOfTestSets = testCaseJsonObject.getAsJsonObject("TestSets").get("Count").getAsInt();

        QueryRequest testsetCollectionRequest = new QueryRequest(testCaseJsonObject.getAsJsonObject("TestSets"));
        testsetCollectionRequest.setFetch(new Fetch("FormattedID"));
        JsonArray testsets = null;
        try {
            testsets = restApi.query(testsetCollectionRequest).getResults();
        } catch (IOException e) {
            e.printStackTrace();
        }
        testsets.add(tsJsonObject);

        JsonObject testCaseUpdate = new JsonObject();
        testCaseUpdate.add("TestSets", testsets);
        UpdateRequest updateTestCaseRequest = new UpdateRequest(testCaseRef, testCaseUpdate);
        UpdateResponse updateTestCaseResponse = null;
        try {
            updateTestCaseResponse = restApi.update(updateTestCaseRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (updateTestCaseResponse.wasSuccessful()) {
            QueryRequest testsetCollectionRequest2 = new QueryRequest(testCaseJsonObject.getAsJsonObject("TestSets"));
            testsetCollectionRequest2.setFetch(new Fetch("FormattedID"));
            try {
                JsonArray testsetsAfterUpdate = restApi.query(testsetCollectionRequest2).getResults();
            } catch (IOException e) {
                e.printStackTrace();
            }
            int numberOfTestSetsAfterUpdate = 0;
            try {
                numberOfTestSetsAfterUpdate = restApi.query(testsetCollectionRequest2).getResults().size();
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int j = 0; j < numberOfTestSetsAfterUpdate; j++) {
            }
        }

    }

    // Add a Test Case Result in test set
    public static void addTCResult(String tcId, String workspaceRef, String tsRef, String tcRef, String filename)
            throws IOException {
        JsonObject newTestCaseResult = new JsonObject();
        // resultString need to update
        newTestCaseResult.addProperty("Verdict", resultString);
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        String timestamp = sdf.format(date);
        newTestCaseResult.addProperty("Date", timestamp);
        newTestCaseResult.addProperty("Build", "Build number# " + timestamp);
        newTestCaseResult.addProperty("Description", "Automaed test case updated.");
        newTestCaseResult.addProperty("TestCase", tcRef);
        newTestCaseResult.addProperty("TestSet", tsRef);
        newTestCaseResult.addProperty("Workspace", workspaceRef);

        CreateRequest createRequest = new CreateRequest("testcaseresult", newTestCaseResult);
        CreateResponse createResponse = restApi.create(createRequest);

        String testCaseResultRef = "";
        if (createResponse.wasSuccessful()) {
            // Read Test Case Result
            testCaseResultRef = Ref.getRelativeRef(createResponse.getObject().get("_ref").getAsString());
        }
        // File handle for text to attach
        RandomAccessFile myImageFileHandle;
        String imageBase64String;
        long attachmentSize;

        // Open file
        myImageFileHandle = new RandomAccessFile(filename, "r");

        // Get and check length
        long longLength = myImageFileHandle.length();
        long maxLength = 5000000;
        if (longLength >= maxLength)
            throw new IOException("File size >= 5 MB Upper limit for Rally.");
        int fileLength = (int) longLength;

        // Read file and return data
        byte[] fileBytes = new byte[fileLength];
        myImageFileHandle.readFully(fileBytes);
        imageBase64String = Base64.encodeBase64String(fileBytes);
        attachmentSize = fileLength;

        // First create AttachmentContent from text string
        JsonObject myAttachmentContent = new JsonObject();
        myAttachmentContent.addProperty("Content", imageBase64String);
        CreateRequest attachmentContentCreateRequest = new CreateRequest("AttachmentContent", myAttachmentContent);
        attachmentContentCreateRequest.addParam("workspace", workspaceRef);
        CreateResponse attachmentContentResponse = restApi.create(attachmentContentCreateRequest);
        String myAttachmentContentRef = attachmentContentResponse.getObject().get("_ref").getAsString();
        // Now create the Attachment itself
        JsonObject myAttachment = new JsonObject();
        myAttachment.addProperty("TestCaseResult", testCaseResultRef);
        myAttachment.addProperty("Content", myAttachmentContentRef);
        myAttachment.addProperty("Name", filename.split("/")[1]);
        myAttachment.addProperty("Description", "Test result file");
        myAttachment.addProperty("ContentType", "text/plain");
        myAttachment.addProperty("Size", attachmentSize);
        CreateRequest attachmentCreateRequest = new CreateRequest("Attachment", myAttachment);
        attachmentCreateRequest.addParam("workspace", workspaceRef);

        CreateResponse attachmentResponse = restApi.create(attachmentCreateRequest);
        String myAttachmentRef = attachmentResponse.getObject().get("_ref").getAsString();
        if (attachmentResponse.wasSuccessful()) {
            System.out.println("Successfully imported " + tcId);
        }
    }

}
