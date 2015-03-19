package spojcrawler;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created by mb1994 on 16/1/15.
 */
public class SPOJCrawler {

    private static SPOJWindow window;
    private final String USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:34.0) Gecko/20100101 Firefox/34.0";
    private final String SPOJ_URL = "http://www.spoj.com";
    private String SPOJ_DIR;
    private String username;
    private String password;
    private List<String> cookies;
    private HttpURLConnection conn;
    private List<String> accepted;
    private List<String> problemIDs;
    private List<String> extensions;

    public SPOJCrawler(String username, String password, String path) {
        this.username = username;
        this.password = password;
        this.SPOJ_DIR = path;
        this.cookies = new ArrayList<String>();
        this.conn = null;
        this.accepted = new ArrayList<String>();
        this.problemIDs = new ArrayList<String>();
        this.extensions = new ArrayList<String>();
    }

    public void crawl() throws Exception {

        long startTime = System.nanoTime();
        
        CookieHandler.setDefault(new CookieManager());
        
        window.appendMsg("Trying to log in....");
        
        String postMsg = preparePOSTMsg();
        sendPOST(SPOJ_URL, postMsg);

        //progress after logging in..
        window.setProgress(1);
        
        String content = getPageContent(SPOJ_URL);
        if (content.contains("logout"))
            window.appendMsg("Logged In!!");
        else {
            window.appendMsg("Could not log in!");
            return;
        }

        //progress after this: 20%.
        window.appendMsg("===================================");
        window.appendMsg("\nFinding all solved questions....");
        findSolvedQs(username);
        window.appendMsg("Found all solved questions successfully.");
        
        //progress after this: 99%.
        window.appendMsg("===================================");
        window.appendMsg("\nStarting to fetch Codes....");
        fetchCodes();
        window.appendMsg("\nFetched all codes....");
        window.appendMsg("===================================");

        //progress after this: 100%.
        window.appendMsg("Logging out now....");
        content = getPageContent(SPOJ_URL + "/logout");
        if(!content.contains(username)) {
            window.appendMsg("Logged out!!");
        } else {
            window.appendMsg("Could not log out!");
        }
        
        long endTime = System.nanoTime();
        long diff = endTime - startTime;
        long seconds = diff / 1000000000;
        long minutes = seconds / 60;
        seconds -= minutes * 60;
        window.appendMsg("Crawling Completed Successfully. Time Taken: " + minutes + " minutes, " + seconds + " seconds");
        window.setProgress(100);
    }

    public void fetchCodes() throws IOException {

        String pCode, pID, link;

        //iterate through and go to links..
        for (int i = 0; i < accepted.size(); i++) {
            pCode = accepted.get(i);
            pID = problemIDs.get(i);

            window.appendMsg("\nproblem code:"+pCode);

            //preparing link:
            link = SPOJ_URL + "/submit/" + pCode + "/id=" + pID;

            //getting its html.
            String html = getPageContent(link);

            //going to that link:
            Document doc = Jsoup.parse(html);

            //create a new file with name=probcode.
            String extension = findExtension(extensions.get(i));
            window.appendMsg("path=" + SPOJ_DIR + pCode + extension);
            PrintWriter writer = new PrintWriter(SPOJ_DIR + pCode + extension, "UTF-8");

            StringTokenizer tokenizer = new StringTokenizer(doc.select("textarea").text(), "\n");
            while (tokenizer.hasMoreTokens())
                writer.write(tokenizer.nextToken() + "\n");
            
            writer.close();
            window.appendMsg("File written successfully.");
            
            window.setProgress(20 + (79*(i+1))/accepted.size());
        }
        
        window.setProgress(99);
    }

    //have to extend this thing.
    public String findExtension(String lang) {
        if (lang == null)
            return ".txt";
        else if (lang.equals("C++"))
            return ".cpp";
        else if (lang.equals("C"))
            return ".c";
        else if (lang.equals("PYT"))
            return ".py";
        else if (lang.equals("JAV"))
            return ".java";
        else
            return ".txt";
    }

    public String getPageContent(String url) throws IOException {

        URL obj = new URL(url);
        conn = (HttpURLConnection) obj.openConnection();

        conn.setRequestMethod("GET");
        conn.setUseCaches(false);

        int responseCode = conn.getResponseCode();
        //window.appendMsg("Sending GET request to URL: " + url);
        //window.appendMsg("Response code:" + responseCode);

        setCookies(conn.getHeaderFields().get("Set-Cookie"));
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        String response = new String();
        while ((inputLine = in.readLine()) != null) {
            if (url.contains("submit"))
                response += inputLine + "\n";
            else
                response += inputLine;
        }
        in.close();
        
        return response;
    }

    public void sendPOST(String url, String postParams) throws Exception {

        URL obj = new URL(url);
        conn = (HttpURLConnection) obj.openConnection();

        //Acts like a browser.
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("Connection", "keep-alive");
        for (String cookie : cookies) {
            conn.addRequestProperty("Cookie", cookie.split(";", 1)[0]);
        }
        conn.setRequestProperty("Host", "www.spoj.com");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Content-Length", Integer.toString(postParams.length()));
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        conn.setDoOutput(true);
        conn.setDoInput(true);

        //Send post request.
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(postParams);
        wr.flush();
        wr.close();

        int responseCode = conn.getResponseCode();
        //window.appendMsg("Sending POST request to URL:" + url);
        //window.appendMsg("Response code:" + responseCode+"\n");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
    }

    public String preparePOSTMsg() throws UnsupportedEncodingException {

        List<String> paramList = new ArrayList<String>();
        paramList.add("login_user=" + URLEncoder.encode(username, "UTF-8"));
        paramList.add("password=" + URLEncoder.encode(password, "UTF-8"));
        paramList.add("submit=" + URLEncoder.encode("Log In", "UTF-8"));

        StringBuilder result = new StringBuilder();
        for (String param : paramList) {
            if (result.length() == 0)
                result.append(param);
            else
                result.append("&" + param);
        }

        return result.toString();
    }

    public void findSolvedQs(String username) throws IOException {

        List<String> lines = new ArrayList<String>();
        String problem = "", status = "", problemID = "";

        BufferedReader urlReader = new BufferedReader(new InputStreamReader(new URL(SPOJ_URL + "/status/" + username + "/signedlist/").openStream()));
        String input;
        while ((input = urlReader.readLine()) != null) {
            lines.add(input);
        }
        window.setProgress(3);
        
        //this reaches progress of 20%.
        String id, date, prob, result, time, mem, lang;
        for (int i = 9; i < lines.size(); i++) {

            if (lines.get(i).charAt(0) == '\\')
                break;

            StringTokenizer tokenizer = new StringTokenizer(lines.get(i), "|");

            id = tokenizer.nextToken().trim();
            date = tokenizer.nextToken().trim();
            prob = tokenizer.nextToken().trim();
            result = tokenizer.nextToken().trim();
            time = tokenizer.nextToken().trim();
            mem = tokenizer.nextToken().trim();
            lang = tokenizer.nextToken().trim();

            if (result.equals("AC") && !accepted.contains(prob)) {
                accepted.add(prob);
                problemIDs.add(id);
                extensions.add(lang);
            }
            
            window.setProgress((3+ 17*(i-8)/(lines.size()-8)));
        }
        
        //20% progress reached.
        window.setProgress(20);
    }

    public void setCookies(List<String> cookie) {
        cookies = cookie;
    }
    
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(SPOJWindow.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(SPOJWindow.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(SPOJWindow.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(SPOJWindow.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        
        window = new SPOJWindow();
        window.setVisible(true);
        window.setTitle("Welcome to SPOJ Crawler.");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }
}